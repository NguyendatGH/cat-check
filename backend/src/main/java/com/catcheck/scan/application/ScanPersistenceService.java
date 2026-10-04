package com.catcheck.scan.application;

import com.catcheck.audit.api.AuditActor;
import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.audit.api.AuditOutcome;
import com.catcheck.audit.api.AuditSubjectType;
import com.catcheck.credit.api.CreditConsumption;
import com.catcheck.credit.api.CreditConsumption.CreditConsumeCommand;
import com.catcheck.credit.api.CreditErrorCode;
import com.catcheck.credit.api.CreditConsumption.ReferenceType;
import com.catcheck.media.api.StoredImage;
import com.catcheck.scan.api.ScanSavedEvent;
import com.catcheck.scan.domain.CaptureSource;
import com.catcheck.scan.domain.QualityFlag;
import com.catcheck.scan.domain.Scan;
import com.catcheck.scan.domain.ScanAnalysis;
import com.catcheck.scan.domain.ScanAssignment;
import com.catcheck.scan.domain.ScanImage;
import com.catcheck.scan.domain.ScanImageStorageProvider;
import com.catcheck.scan.domain.ScanStatus;
import com.catcheck.scan.domain.ScanThresholds;
import com.catcheck.scan.domain.StoreImageReason;
import com.catcheck.scan.domain.port.CatOwnershipPort;
import com.catcheck.scan.domain.port.ScanAnalysisRepository;
import com.catcheck.scan.domain.port.ScanImageRepository;
import com.catcheck.scan.domain.port.ScanRepository;
import com.catcheck.scan.application.spi.OnboardingProgressPort;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.id.UuidV7;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Phần TRONG transaction của luồng {@code POST /scans} (p7 §7.4.5, TD-02) — {@code INSERT scan}
 * + {@code scan_analysis} (+{@code scan_image}) + trừ credit trong MỘT giao dịch.
 *
 * <p>Gọi {@link CreditConsumption#consume}/{@link CreditConsumption#consumeTrialScan} ở ĐÂY vì
 * hai hàm đó là {@code Propagation.MANDATORY} — bắt buộc có transaction đang mở, và class này là
 * nơi transaction được mở ({@code @Transactional} chỉ hợp lệ trong {@code ..application..},
 * R12).</p>
 */
@Service
public class ScanPersistenceService {

    private final ScanRepository scanRepository;
    private final ScanAnalysisRepository scanAnalysisRepository;
    private final ScanImageRepository scanImageRepository;
    private final CreditConsumption creditConsumption;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final OnboardingProgressPort onboardingProgressPort;
    private final UuidV7 uuidV7;
    private final Clock clock;

    public ScanPersistenceService(
            ScanRepository scanRepository,
            ScanAnalysisRepository scanAnalysisRepository,
            ScanImageRepository scanImageRepository,
            CreditConsumption creditConsumption,
            AuditLogService auditLogService,
            ApplicationEventPublisher eventPublisher,
            OnboardingProgressPort onboardingProgressPort,
            UuidV7 uuidV7,
            Clock clock) {
        this.scanRepository = scanRepository;
        this.scanAnalysisRepository = scanAnalysisRepository;
        this.scanImageRepository = scanImageRepository;
        this.creditConsumption = creditConsumption;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
        this.onboardingProgressPort = onboardingProgressPort;
        this.uuidV7 = uuidV7;
        this.clock = clock;
    }

    /**
     * @param cat           chụp hồ sơ mèo (null khi {@code assignment = SHARED_UNKNOWN})
     * @param trialRegime   {@code true} nếu tài khoản đang ở chế độ trial (chưa từng kích hoạt gói,
     *                      xác định TRƯỚC khi chạy pipeline — p7 §7.4.5)
     * @param storeImageAllowed gói + not-trial cho phép lưu ảnh (đã kiểm entitlement TRƯỚC khi gọi)
     * @param storedImage   ảnh ĐÃ ghi ra {@code ImageStorage} TRƯỚC khi mở transaction này
     *                      (rỗng nếu không lưu ảnh)
     */
    @Transactional(timeout = 5)
    public ScanSubmitResult persist(SubmitScanCommand cmd, PipelineOutcome outcome,
                                     CatOwnershipPort.CatSnapshot cat, boolean trialRegime,
                                     boolean storeImageAllowed, Optional<StoredImage> storedImage) {

        Instant now = clock.instant();
        UUID scanId = uuidV7.generate();

        boolean inconclusive = outcome.inconclusive();
        boolean storeImage = storeImageAllowed && !trialRegime && !inconclusive;
        StoreImageReason storeImageReason = storeImage ? null
                : inconclusive ? StoreImageReason.INCONCLUSIVE
                : trialRegime ? StoreImageReason.TRIAL
                : StoreImageReason.PLAN_OFF;

        Scan scan = new Scan(
                scanId, cmd.userId(), cat == null ? null : cat.catId(), cmd.assignment(),
                cmd.capturedAt(), cmd.captureSource(), cmd.deviceHint(), trialRegime, storeImage,
                storeImageReason, ScanStatus.PENDING, outcome.chartId(), cmd.idempotencyKey(), now);
        scan = scanRepository.save(scan);

        ScanAnalysis analysis = mapAnalysis(uuidV7.generate(), scanId, outcome, now);
        analysis = scanAnalysisRepository.save(analysis);

        UUID creditLedgerId = null;
        boolean creditCharged = false;
        Integer creditBalanceAfter = null;

        if (!inconclusive) {
            if (trialRegime) {
                boolean consumed = creditConsumption.consumeTrialScan(cmd.userId());
                if (!consumed) {
                    throw new BusinessRuleException(CreditErrorCode.CREDIT_INSUFFICIENT, 0, 1);
                }
            } else {
                CreditConsumption.CreditCharge charge = creditConsumption.consume(cmd.userId(),
                        new CreditConsumeCommand(1, cmd.idempotencyKey(), ReferenceType.SCAN, scanId, null));
                creditCharged = true;
                creditBalanceAfter = charge.balanceAfter();
                if (!charge.ledgerEntries().isEmpty()) {
                    creditLedgerId = charge.ledgerEntries().getFirst().ledgerId();
                }
            }
        }

        scan.markAnalyzed(analysis.getId(), creditLedgerId, now);
        scan = scanRepository.save(scan);

        Instant imageExpiresAt = null;
        if (storeImage && storedImage.isPresent()) {
            StoredImage stored = storedImage.get();
            ScanImage image = new ScanImage(
                    uuidV7.generate(), scanId,
                    "CLOUDINARY".equals(stored.provider()) ? ScanImageStorageProvider.CLOUDINARY : ScanImageStorageProvider.LOCAL,
                    stored.key().value(), stored.contentType(), stored.sizeBytes(),
                    outcome.imageWidth(), outcome.imageHeight(), null,
                    now.plus(Duration.ofDays(14)), now);
            scanImageRepository.save(image);
            imageExpiresAt = image.getExpiresAt();
        }

        auditLogService.record(AuditEvent.builder()
                .actor(AuditActor.user(cmd.userId(), "USER"))
                .subject(AuditSubjectType.SCAN, cmd.userId())
                .action(inconclusive ? "SCAN.INCONCLUSIVE" : "SCAN.SAVED")
                .outcome(AuditOutcome.SUCCESS)
                .meta("scanId", scanId.toString())
                .meta("classification", outcome.classification().name())
                .build());

        if (!inconclusive) {
            // Cot moc cuoi cua onboarding (p4 §4.4: COMPLETED = "da quet lan dau"). Goi thang
            // qua SPI chu khong dua vao ScanSavedEvent ngay duoi: event do khong co listener
            // nao, va identity khong duoc phep phu thuoc scan nen khong dat listener ben do.
            onboardingProgressPort.markFirstScanCompleted(cmd.userId());
            // H15.75 — BẮT BUỘC đứng trước publishEvent. Listener rule của `insight` chạy đồng
            // bộ trong CHÍNH transaction này (p7 §7.4.2/§7.4.5) và đọc lịch sử bằng SQL thô
            // (ScanQueryRepository.findRecentForRules). Lệnh ghi ORM ở trên mới nằm trong
            // persistence context, chưa xuống DB, nên không đẩy trước thì câu SQL đó thiếu đúng
            // scan vừa tạo: R4 streak=2 cần 3 lần quét mới nổ thay vì 2 (đã đo thật).
            // Đây chỉ là flush, KHÔNG phải commit — rollback vẫn cuốn cả health_flag lẫn scan.
            scanRepository.flushPendingWrites();
            eventPublisher.publishEvent(new ScanSavedEvent(
                    scanId, cat == null ? null : cat.catId(), cmd.userId(), cmd.assignment().name(),
                    outcome.phValue(), outcome.classification().name(), outcome.confidence(),
                    outcome.nearBoundary(), outcome.calibrationMethod().name(), cmd.capturedAt(),
                    outcome.chartIsPlaceholder()));
        }

        return new ScanSubmitResult(
                inconclusive ? null : scanId,
                cmd.idempotencyKey(),
                cat == null ? null : cat.catId(),
                cat == null ? null : cat.name(),
                cmd.assignment().name(),
                cmd.capturedAt(),
                scan.getStatus().name(),
                outcome,
                creditCharged,
                creditBalanceAfter,
                trialRegime,
                storeImage,
                imageExpiresAt,
                storeImageReason == null ? null : storeImageReason.name(),
                inconclusive ? null : cmd.capturedAt().plusSeconds(ScanThresholds.REASSIGN_WINDOW_HOURS * 3600L),
                ScanThresholds.REASSIGN_MAX_COUNT,
                false);
    }

    /** Trả nguyên kết quả cũ khi client retry cùng {@code Idempotency-Key} (p5 R8). */
    public ScanSubmitResult replay(Scan existing, ScanAnalysis analysis, CatOwnershipPort.CatSnapshot cat) {
        boolean inconclusive = analysis.getClassification().name().equals("INCONCLUSIVE");
        PipelineOutcome outcome = PipelineOutcomeMapper.fromEntity(analysis);
        return new ScanSubmitResult(
                inconclusive ? null : existing.getId(),
                existing.getIdempotencyKey(),
                existing.getCatId(),
                cat == null ? null : cat.name(),
                existing.getAssignment().name(),
                existing.getCapturedAt(),
                existing.getStatus().name(),
                outcome,
                existing.getCreditLedgerId() != null,
                null,
                existing.isTrial(),
                existing.isStoreImage(),
                null,
                existing.getStoreImageReason() == null ? null : existing.getStoreImageReason().name(),
                existing.isReassignable(clock.instant())
                        ? existing.getCapturedAt().plusSeconds(ScanThresholds.REASSIGN_WINDOW_HOURS * 3600L) : null,
                ScanThresholds.REASSIGN_MAX_COUNT - existing.getReassignCount(),
                true);
    }

    private ScanAnalysis mapAnalysis(UUID analysisId, UUID scanId, PipelineOutcome outcome, Instant now) {
        ScanAnalysis analysis = new ScanAnalysis(analysisId, scanId, now);
        analysis.setPhValue(outcome.phValue());
        analysis.setPhLow(outcome.phLow());
        analysis.setPhHigh(outcome.phHigh());
        analysis.setClassification(outcome.classification());
        analysis.setConfidence(outcome.confidence());
        analysis.setNearBoundary(outcome.nearBoundary());
        analysis.setLabL(outcome.labL());
        analysis.setLabA(outcome.labA());
        analysis.setLabB(outcome.labB());
        analysis.setLabSpreadDe00(outcome.labSpreadDe00());
        analysis.setBlobCount(outcome.blobCount());
        analysis.setIndicatorPixelRatio(outcome.indicatorPixelRatio());
        analysis.setSubstrateLabL(outcome.substrateLabL());
        analysis.setSubstrateLabA(outcome.substrateLabA());
        analysis.setSubstrateLabB(outcome.substrateLabB());
        analysis.setDeltaEMin(outcome.deltaEMin());
        analysis.setPerpResidualDe00(outcome.perpResidualDe00());
        analysis.setMatchPercent(outcome.matchPercent());
        analysis.setMatchedPointId(outcome.matchedPointId());
        analysis.setMatchedSegmentK(outcome.matchedSegmentK());
        analysis.setMatchedT(outcome.matchedT());
        analysis.setCalibrationMethod(outcome.calibrationMethod());
        analysis.setCalibrationResidualDe00(outcome.calibrationResidualDe00());
        analysis.setCalibration(outcome.calibration());
        analysis.setQualityMetrics(outcome.qualityMetrics());
        analysis.setQualityFlags(toFlagMaps(outcome.qualityFlags()));
        analysis.setChartId(outcome.chartId());
        analysis.setChartVersion(outcome.chartVersion());
        analysis.setEngineVersion(outcome.engineVersion());
        analysis.setProcessingMs(outcome.processingMs());
        return analysis;
    }

    private List<Map<String, Object>> toFlagMaps(List<QualityFlag> flags) {
        return flags.stream().map(f -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", f.code().name());
            m.put("severity", f.severity().name());
            return (Map<String, Object>) m;
        }).toList();
    }
}
