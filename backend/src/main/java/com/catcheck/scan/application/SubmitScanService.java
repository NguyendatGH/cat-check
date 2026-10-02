package com.catcheck.scan.application;

import com.catcheck.cat.api.CatErrorCode;
import com.catcheck.credit.api.CreditConsumption;
import com.catcheck.credit.api.CreditErrorCode;
import com.catcheck.credit.api.EntitlementQuery;
import com.catcheck.credit.domain.PlanFeature;
import com.catcheck.media.api.ImageStorage;
import com.catcheck.media.api.ImageUpload;
import com.catcheck.media.api.StoredImage;
import com.catcheck.scan.api.ScanErrorCode;
import com.catcheck.scan.domain.ScanAnalysis;
import com.catcheck.scan.domain.ScanAssignment;
import com.catcheck.scan.domain.ScanThresholds;
import com.catcheck.scan.domain.port.CatOwnershipPort;
import com.catcheck.scan.domain.port.ScanAnalysisRepository;
import com.catcheck.scan.domain.port.ScanRepository;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.ConflictException;
import com.catcheck.shared.error.NotFoundException;
import com.catcheck.shared.error.PermissionDeniedException;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Điểm vào cho {@code POST /api/v1/scans} — điều phối TOÀN BỘ luồng TD-02 (p7 §7.4.5):
 * idempotency → kiểm quyền sở hữu mèo → chặn sớm bằng credit (read-only) → chạy pipeline (NGOÀI
 * transaction) → lưu ảnh (NGOÀI transaction) → {@link ScanPersistenceService#persist} (TRONG một
 * transaction, trừ credit).
 *
 * <p><b>Không có {@code @Transactional} ở đây</b> — class này CHỦ Ý nằm ngoài mọi transaction
 * DB; phần duy nhất mở transaction là {@link ScanPersistenceService} (R12).</p>
 */
@Service
public class SubmitScanService {

    private final ScanRepository scanRepository;
    private final ScanAnalysisRepository scanAnalysisRepository;
    private final ColorPipelineService pipelineService;
    private final ScanPersistenceService persistenceService;
    private final CatOwnershipPort catOwnershipPort;
    private final CreditConsumption creditConsumption;
    private final EntitlementQuery entitlementQuery;
    private final ImageStorage imageStorage;
    private final Clock clock;

    /**
     * Khoá chống trùng lặp trong cùng một instance (p6 §6.4.1 {@code SCAN_IN_PROGRESS}).
     *
     * <p><b>Giới hạn đã biết:</b> chỉ hoạt động trong PHẠM VI MỘT instance — {@code shared.idempotency}
     * (cơ chế TTL 24h cấp toàn hệ thống mà p7 §7.4.5 mô tả) chưa tồn tại ở M0 (ngoài phạm vi sở
     * hữu của A6, xem {@code docs/handovers/A6.md}). Lưới an toàn cuối vẫn là
     * {@code UNIQUE(user_id, idempotency_key)} ở DB — hai instance đụng nhau sẽ có một cái nhận
     * lỗi ràng buộc UNIQUE, ánh xạ về {@link ScanErrorCode#SCAN_IN_PROGRESS} ở tầng gọi.</p>
     */
    private final ConcurrentHashMap<String, Boolean> inFlight = new ConcurrentHashMap<>();

    public SubmitScanService(
            ScanRepository scanRepository,
            ScanAnalysisRepository scanAnalysisRepository,
            ColorPipelineService pipelineService,
            ScanPersistenceService persistenceService,
            CatOwnershipPort catOwnershipPort,
            CreditConsumption creditConsumption,
            EntitlementQuery entitlementQuery,
            ImageStorage imageStorage,
            Clock clock) {
        this.scanRepository = scanRepository;
        this.scanAnalysisRepository = scanAnalysisRepository;
        this.pipelineService = pipelineService;
        this.persistenceService = persistenceService;
        this.catOwnershipPort = catOwnershipPort;
        this.creditConsumption = creditConsumption;
        this.entitlementQuery = entitlementQuery;
        this.imageStorage = imageStorage;
        this.clock = clock;
    }

    public ScanSubmitResult submit(SubmitScanCommand cmd) {
        Optional<ScanSubmitResult> replay = tryReplay(cmd);
        if (replay.isPresent()) {
            return replay.get();
        }

        String inFlightKey = cmd.userId() + ":" + cmd.idempotencyKey();
        if (inFlight.putIfAbsent(inFlightKey, Boolean.TRUE) != null) {
            throw new ConflictException(ScanErrorCode.SCAN_IN_PROGRESS);
        }
        try {
            // Có thể một request song song đã hoàn tất giữa lúc kiểm tra replay và lúc giành khoá.
            Optional<ScanSubmitResult> raced = tryReplay(cmd);
            if (raced.isPresent()) {
                return raced.get();
            }
            return doSubmit(cmd);
        } finally {
            inFlight.remove(inFlightKey);
        }
    }

    private Optional<ScanSubmitResult> tryReplay(SubmitScanCommand cmd) {
        return scanRepository.findByUserIdAndIdempotencyKey(cmd.userId(), cmd.idempotencyKey())
                .map(existing -> {
                    ScanAnalysis analysis = scanAnalysisRepository.findCurrentByScanId(existing.getId())
                            .orElseThrow(() -> new NotFoundException(ScanErrorCode.SCAN_ANALYSIS_NOT_FOUND));
                    CatOwnershipPort.CatSnapshot cat = existing.getCatId() == null
                            ? null
                            : catOwnershipPort.findSnapshot(existing.getCatId()).orElse(null);
                    return persistenceService.replay(existing, analysis, cat);
                });
    }

    private ScanSubmitResult doSubmit(SubmitScanCommand cmd) {
        validateMetadata(cmd);

        CatOwnershipPort.CatSnapshot cat = null;
        if (cmd.assignment() == ScanAssignment.ASSIGNED) {
            cat = catOwnershipPort.findSnapshot(cmd.catId())
                    .orElseThrow(() -> new PermissionDeniedException(ScanErrorCode.CAT_NOT_OWNED));
            if (!cat.isOwnedBy(cmd.userId())) {
                throw new PermissionDeniedException(ScanErrorCode.CAT_NOT_OWNED);
            }
            if (cat.archived()) {
                throw new ConflictException(CatErrorCode.CAT_ARCHIVED);
            }
        }

        // Chặn sớm bằng credit (read-only, không khoá dòng) TRƯỚC KHI tốn CPU chạy pipeline —
        // xem javadoc credit.api.CreditConsumption.
        boolean trialRegime = creditConsumption.hasTrialScanRemaining(cmd.userId());
        boolean creditAvailable = creditConsumption.hasAvailableCredit(cmd.userId());
        if (!trialRegime && !creditAvailable) {
            throw new BusinessRuleException(CreditErrorCode.CREDIT_INSUFFICIENT, 0, 1);
        }

        PipelineOutcome outcome = pipelineService.analyze(cmd.imageBytes(), cmd.roi(), cmd.cardQuadHint());

        boolean storeImageAllowed = entitlementQuery.isFeatureEnabled(cmd.userId(), PlanFeature.STORE_IMAGE);
        boolean willStoreImage = storeImageAllowed && !trialRegime && !outcome.inconclusive();

        Optional<StoredImage> storedImage = Optional.empty();
        if (willStoreImage) {
            storedImage = Optional.of(imageStorage.put("scan-image", new ImageUpload(
                    new ByteArrayInputStream(cmd.imageBytes()), "scan.jpg",
                    outcome.imageContentType(), cmd.imageBytes().length)));
        }

        try {
            return persistenceService.persist(cmd, outcome, cat, trialRegime, storeImageAllowed, storedImage);
        } catch (RuntimeException ex) {
            // Transaction rollback (vd. hết credit do race) — dọn file đã ghi ngoài transaction
            // (p7 §7.4.5 điểm 2: staging/ghi file KHÔNG nằm trong transaction, nên dọn thủ công ở
            // đây thay vì TransactionSynchronization; xem docs/handovers/A6.md).
            storedImage.ifPresent(stored -> safeDelete(stored));
            throw ex;
        }
    }

    private void safeDelete(StoredImage stored) {
        try {
            imageStorage.delete(stored.key());
        } catch (RuntimeException ignored) {
            // best-effort — không được che lỗi gốc của nhánh persist()
        }
    }

    private void validateMetadata(SubmitScanCommand cmd) {
        if (cmd.assignment() == ScanAssignment.UNASSIGNED) {
            throw new BusinessRuleException(ScanErrorCode.SCAN_METADATA_INVALID);
        }
        if (cmd.assignment() == ScanAssignment.ASSIGNED && cmd.catId() == null) {
            throw new BusinessRuleException(ScanErrorCode.SCAN_METADATA_INVALID);
        }
        if (cmd.assignment() == ScanAssignment.SHARED_UNKNOWN && cmd.catId() != null) {
            throw new BusinessRuleException(ScanErrorCode.SCAN_METADATA_INVALID);
        }
        Instant now = clock.instant();
        if (cmd.capturedAt().isAfter(now.plusSeconds(2 * 3600L))) {
            throw new BusinessRuleException(ScanErrorCode.SCAN_METADATA_INVALID);
        }
        if (cmd.imageBytes().length > ScanThresholds.MAX_UPLOAD_BYTES) {
            throw new BusinessRuleException(ScanErrorCode.IMAGE_TOO_LARGE,
                    cmd.imageBytes().length, ScanThresholds.MAX_UPLOAD_BYTES);
        }
    }
}
