package com.catcheck.scan.application;

import com.catcheck.cat.api.CatErrorCode;
import com.catcheck.credit.api.CreditConsumption;
import com.catcheck.credit.api.CreditErrorCode;
import com.catcheck.scan.api.ScanErrorCode;
import com.catcheck.scan.domain.ScanAnalysis;
import com.catcheck.scan.domain.ScanAssignment;
import com.catcheck.scan.domain.port.CatOwnershipPort;
import com.catcheck.scan.domain.port.ScanAnalysisRepository;
import com.catcheck.scan.domain.port.ScanRepository;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.ConflictException;
import com.catcheck.shared.error.NotFoundException;
import com.catcheck.shared.error.PermissionDeniedException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Đường nhập scan THỦ CÔNG (dev-only) — bản song song tối giản của {@link SubmitScanService}
 * cho trường hợp "đã biết pH, không có ảnh".
 *
 * <p><b>Dùng lại nguyên đường ghi thật:</b> mọi thứ sau khi có {@link PipelineOutcome} đều đi
 * qua {@link ScanPersistenceService#persist} y hệt đường {@code POST /scans} — tức là vẫn một
 * transaction cho {@code scan} + {@code scan_analysis} + trừ credit FEFO, vẫn ghi
 * {@code credit_ledger}, vẫn phát {@code ScanSavedEvent} (health flag R1–R4), vẫn đánh dấu
 * mốc onboarding. Không nhánh nào ở đây tự ghi DB.
 *
 * <p><b>Khác {@link SubmitScanService} ở đúng ba điểm:</b> (1) không chạy
 * {@link ColorPipelineService} mà gọi {@link ManualPipelineOutcomeFactory}; (2) không lưu ảnh —
 * truyền {@code storeImageAllowed = false} nên {@code store_image_reason} luôn có giá trị theo
 * đúng CHECK của p4 D1; (3) chỉ đòi credit khi kết quả KHÔNG phải {@code INCONCLUSIVE}, vì ở
 * đây kết quả đã biết trước khi chạm DB (đường ảnh phải chặn sớm để không tốn CPU pipeline).
 *
 * <p>Không có {@code @Transactional} ở lớp này — transaction duy nhất mở ở
 * {@link ScanPersistenceService} (R12), giống {@link SubmitScanService}.
 */
@Service
public class ManualScanIngestService {

    /**
     * {@link SubmitScanCommand} có bất biến "{@code imageBytes} không rỗng" — bất biến ĐÚNG cho
     * đường ảnh và không được nới ra. Đường nhập tay không có ảnh, mà
     * {@link ScanPersistenceService#persist} không hề đọc {@code imageBytes}, nên truyền một
     * byte giữ chỗ là cách dùng lại được record đó mà không làm yếu kiểm tra của đường thật.
     */
    private static final byte[] NO_IMAGE = new byte[] {0};

    private final ScanRepository scanRepository;
    private final ScanAnalysisRepository scanAnalysisRepository;
    private final ScanPersistenceService persistenceService;
    private final ManualPipelineOutcomeFactory outcomeFactory;
    private final CatOwnershipPort catOwnershipPort;
    private final CreditConsumption creditConsumption;
    private final Clock clock;

    public ManualScanIngestService(ScanRepository scanRepository,
                                   ScanAnalysisRepository scanAnalysisRepository,
                                   ScanPersistenceService persistenceService,
                                   ManualPipelineOutcomeFactory outcomeFactory,
                                   CatOwnershipPort catOwnershipPort,
                                   CreditConsumption creditConsumption,
                                   Clock clock) {
        this.scanRepository = scanRepository;
        this.scanAnalysisRepository = scanAnalysisRepository;
        this.persistenceService = persistenceService;
        this.outcomeFactory = outcomeFactory;
        this.catOwnershipPort = catOwnershipPort;
        this.creditConsumption = creditConsumption;
        this.clock = clock;
    }

    /** Gọi lại cùng {@code idempotencyKey} trả nguyên kết quả cũ, KHÔNG trừ credit lần hai (p5 R8). */
    public ScanSubmitResult ingest(ManualScanCommand cmd) {
        Optional<ScanSubmitResult> replay = tryReplay(cmd.userId(), cmd.idempotencyKey());
        if (replay.isPresent()) {
            return replay.get();
        }

        validate(cmd);

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

        PipelineOutcome outcome = outcomeFactory.fromManualPh(
                cmd.phValue(), cmd.forcedClassification(), cmd.confidence(), cmd.qualityFlags());

        boolean trialRegime = creditConsumption.hasTrialScanRemaining(cmd.userId());
        if (!outcome.inconclusive() && !trialRegime && !creditConsumption.hasAvailableCredit(cmd.userId())) {
            throw new BusinessRuleException(CreditErrorCode.CREDIT_INSUFFICIENT, 0, 1);
        }

        SubmitScanCommand command = new SubmitScanCommand(
                cmd.userId(), cmd.catId(), cmd.assignment(), cmd.capturedAt(), cmd.captureSource(),
                cmd.deviceHint(), cmd.idempotencyKey(), null, null, NO_IMAGE);

        return persistenceService.persist(command, outcome, cat, trialRegime, false, Optional.empty());
    }

    private Optional<ScanSubmitResult> tryReplay(UUID userId, String idempotencyKey) {
        return scanRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .map(existing -> {
                    ScanAnalysis analysis = scanAnalysisRepository.findCurrentByScanId(existing.getId())
                            .orElseThrow(() -> new NotFoundException(ScanErrorCode.SCAN_ANALYSIS_NOT_FOUND));
                    CatOwnershipPort.CatSnapshot cat = existing.getCatId() == null
                            ? null
                            : catOwnershipPort.findSnapshot(existing.getCatId()).orElse(null);
                    return persistenceService.replay(existing, analysis, cat);
                });
    }

    private void validate(ManualScanCommand cmd) {
        ScanAssignment assignment = cmd.assignment();
        if (assignment == null || assignment == ScanAssignment.UNASSIGNED) {
            throw new BusinessRuleException(ScanErrorCode.SCAN_METADATA_INVALID);
        }
        if (assignment == ScanAssignment.ASSIGNED && cmd.catId() == null) {
            throw new BusinessRuleException(ScanErrorCode.SCAN_METADATA_INVALID);
        }
        if (assignment == ScanAssignment.SHARED_UNKNOWN && cmd.catId() != null) {
            throw new BusinessRuleException(ScanErrorCode.SCAN_METADATA_INVALID);
        }
        Instant capturedAt = cmd.capturedAt();
        if (capturedAt == null) {
            throw new BusinessRuleException(ScanErrorCode.SCAN_METADATA_INVALID);
        }
        // Cùng cửa sổ +2h như đường ảnh (p4 D1 CHECK ck_scan_captured_at). Lùi về quá khứ không
        // giới hạn là CHỦ Ý: dựng 60–90 ngày lịch sử demo chính là mục đích của lớp này.
        if (capturedAt.isAfter(clock.instant().plusSeconds(2 * 3600L))) {
            throw new BusinessRuleException(ScanErrorCode.SCAN_METADATA_INVALID);
        }
    }
}
