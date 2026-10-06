package com.catcheck.scan.application;

import com.catcheck.scan.api.ChartBackfill;
import com.catcheck.scan.domain.ScanAnalysis;
import com.catcheck.scan.domain.ScanAnalysisRecompute;
import com.catcheck.scan.domain.Scan;
import com.catcheck.scan.domain.ScanClassification;
import com.catcheck.scan.domain.ScanRecomputeImpact;
import com.catcheck.scan.domain.color.ChartCatalog;
import com.catcheck.scan.domain.color.ChartMatcher;
import com.catcheck.scan.domain.color.Lab;
import com.catcheck.scan.domain.color.PhBandClassifier;
import com.catcheck.scan.domain.color.PhChart;
import com.catcheck.scan.domain.port.ScanAnalysisRecomputeRepository;
import com.catcheck.scan.domain.port.ScanAnalysisRepository;
import com.catcheck.scan.domain.port.ScanRepository;
import com.catcheck.shared.id.UuidV7;
import com.catcheck.shared.job.JobContext;
import com.catcheck.shared.job.JobOutcome;
import com.catcheck.shared.job.JobTriggerType;
import com.catcheck.shared.job.application.JobRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Backfill bảng màu — L33, L34, L35 (p8 §8.4.12 mục (c); phép tính ở p6 §6.5.4, §6.6.5).
 *
 * <h2>Chỉ chạy lại S8→S10, từ Lab đã lưu</h2>
 * <p>Không cần ảnh gốc: {@code scan_analysis.lab_l/a/b} là màu đã hiệu chuẩn (sau S1–S7), nên
 * đổi bảng màu chỉ ảnh hưởng bước khớp (S8) và bước phân loại (S10). Nhờ vậy backfill chạy
 * được cả với scan trial (không lưu ảnh) và cả sau khi ảnh đã bị xoá theo retention 14 ngày —
 * đúng câu chốt của p6 §6.5.4.
 *
 * <h2>Confidence (S9) KHÔNG được tính lại — xuống thang có ý thức</h2>
 * <p>p6 §6.5.4 viết "S8 → S9 → S10", nhưng {@code ConfidenceCalculator.Inputs} cần
 * {@code blurVariance}, {@code clipHigh/Low}, {@code lumaGradient} — những đại lượng của ảnh,
 * chỉ còn tồn tại dưới dạng khoá tự do trong {@code quality_metrics JSONB} và <b>không có schema
 * nào bảo đảm khoá đó tồn tại</b>. Tính lại từ khoá đoán được sẽ cho ra confidence sai một cách
 * im lặng, tệ hơn hẳn việc giữ nguyên. Vì vậy: giữ confidence gốc, và chỉ áp <b>trần 0.60</b> khi
 * bảng màu mới còn {@code is_placeholder} (p6 §6.6.6). Khoảng tin cậy giữ nguyên ĐỘ RỘNG rồi
 * tịnh tiến theo pH mới — nhờ đó cờ {@code near_boundary} (p6 §6.7.2) vẫn có nghĩa. Ghi handoff
 * H15.161.</p>
 *
 * <h2>Vì sao phải là job nền</h2>
 * <p>Cửa sổ mặc định P90D là hàng chục nghìn dòng. Chạy đồng bộ trong request nghĩa là giữ một
 * Tomcat thread vài phút và client nhận timeout của proxy chứ không nhận kết quả — nên L33/L35
 * trả {@code 202} và công việc đi qua {@link JobRunner} để có đúng một dòng {@code job_run} mỗi
 * lần chạy (p12 §12.6.1 quy tắc 4).</p>
 */
@Service
public class ChartBackfillService implements ChartBackfill {

    private static final Logger log = LoggerFactory.getLogger(ChartBackfillService.class);

    /** Tên job — khớp {@code job_run.job_name VARCHAR(64)}, không viết tắt (p12 §12.6). */
    public static final String PREVIEW_JOB = "chart-backfill-preview";

    public static final String APPLY_JOB = "chart-backfill-apply";

    /**
     * "Cùng major" của p6 §6.5.4. {@code PipelineOutcome.ENGINE_VERSION} là {@code "1.0.0"};
     * hàng nhập tay dùng {@code "manual-1.0.0"} nên bị loại bởi chính tiền tố này — đúng ý, vì
     * pH nhập tay không sinh ra từ Lab và không tính lại được.
     */
    private static final String ENGINE_MAJOR_PREFIX =
            PipelineOutcome.ENGINE_VERSION.substring(0, PipelineOutcome.ENGINE_VERSION.indexOf('.') + 1);

    /** Trần confidence khi bảng màu chưa hiệu chuẩn (p6 §6.6.6). */
    private static final BigDecimal PLACEHOLDER_CONFIDENCE_CAP = new BigDecimal("0.600");

    /** Nửa độ rộng khoảng tin cậy mặc định khi dòng gốc không lưu {@code ph_low}/{@code ph_high}. */
    private static final BigDecimal DEFAULT_CI_HALF_WIDTH = new BigDecimal("0.2");

    /** Số dòng ví dụ trả về ở L34 — đủ để admin nhìn thấy hình dạng thay đổi, không phải để duyệt tay. */
    private static final int SAMPLE_LIMIT = 10;

    private final ScanAnalysisRepository analysisRepository;
    private final ScanRepository scanRepository;
    private final ScanAnalysisRecomputeRepository recomputeRepository;
    private final ChartCatalog chartCatalog;
    private final JobRunner jobRunner;
    private final TaskExecutor executor;
    private final UuidV7 uuidV7;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public ChartBackfillService(ScanAnalysisRepository analysisRepository,
                                ScanRepository scanRepository,
                                ScanAnalysisRecomputeRepository recomputeRepository,
                                ChartCatalog chartCatalog,
                                JobRunner jobRunner,
                                @Qualifier("chartBackfillExecutor") TaskExecutor executor,
                                UuidV7 uuidV7,
                                Clock clock,
                                PlatformTransactionManager transactionManager) {
        this.analysisRepository = analysisRepository;
        this.scanRepository = scanRepository;
        this.recomputeRepository = recomputeRepository;
        this.chartCatalog = chartCatalog;
        this.jobRunner = jobRunner;
        this.executor = executor;
        this.uuidV7 = uuidV7;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // ------------------------------------------------------------------ L33

    @Override
    public void startPreview(UUID chartId, Period window) {
        launch(PREVIEW_JOB, context -> runPreview(context, chartId, window));
    }

    // ------------------------------------------------------------------ L34

    @Override
    @Transactional(readOnly = true)
    public BackfillImpact impact(UUID chartId) {
        ScanRecomputeImpact impact = recomputeRepository.impactByChartId(chartId);
        List<BackfillSample> samples = impact.evaluated() == 0
                ? List.of()
                : recomputeRepository.findTopByChartIdOrderByAbsDeltaPhDesc(chartId, SAMPLE_LIMIT).stream()
                        .map(row -> new BackfillSample(
                                row.getScanAnalysisId(),
                                row.getPhValue(),
                                row.getClassification() == null ? null : row.getClassification().name(),
                                row.getDeltaPh(),
                                row.isFlippedClassification()))
                        .toList();
        return new BackfillImpact(impact.evaluated(), impact.flipped(), impact.maxAbsDeltaPh(),
                impact.jobId(), samples);
    }

    // ------------------------------------------------------------------ L35

    @Override
    public void startApply(UUID chartId) {
        launch(APPLY_JOB, context -> runApply(context, chartId));
    }

    // ------------------------------------------------------------- thân job

    /**
     * Thân L33, tách ra {@code public} để test gọi trực tiếp không cần executor — nếu chỉ gọi
     * được qua {@link #startPreview} thì test phải chờ một luồng khác xong, đúng khuôn làm nên
     * test chập chờn (p17 §17.6.7).
     */
    public JobOutcome runPreview(JobContext context, UUID chartId, Period window) {
        PhChart chart = requireChart(chartId);
        List<PhBandClassifier.Band> bands = chartCatalog.findGlobalBands();
        Instant from = windowStart(window);

        // Bảng này là NHÁP (p4 D4): xoá lượt trước rồi mới ghi, không thì hai lần bấm "xem
        // trước" cộng dồn và con số "bị lật phân loại" của L34 đếm gấp đôi.
        int removed = recomputeRepository.deleteByChartId(chartId);
        long total = analysisRepository.countBackfillCandidates(from, ENGINE_MAJOR_PREFIX);
        log.info("Backfill preview chart={} window={} ungVien={} xoaNhapCu={}",
                chartId, window, total, removed);

        int processed = 0;
        int failed = 0;
        for (int offset = 0; offset < total; offset += BATCH_SIZE) {
            List<ScanAnalysis> batch =
                    analysisRepository.findBackfillCandidates(from, ENGINE_MAJOR_PREFIX, BATCH_SIZE, offset);
            if (batch.isEmpty()) {
                break;
            }
            List<ScanAnalysisRecompute> rows = new ArrayList<>(batch.size());
            for (ScanAnalysis analysis : batch) {
                Recomputed recomputed = recompute(analysis, chart, bands);
                if (recomputed == null) {
                    failed++;
                    continue;
                }
                rows.add(new ScanAnalysisRecompute(
                        uuidV7.generate(),
                        analysis.getId(),
                        chartId,
                        chart.version(),
                        recomputed.phValue(),
                        recomputed.classification(),
                        recomputed.confidence(),
                        recomputed.deltaPh(),
                        recomputed.flipped(),
                        context.runId(),
                        clock.instant()));
                processed++;
            }
            recomputeRepository.saveAll(rows);
        }
        return JobOutcome.of(processed, 0, failed, null);
    }

    /** Thân L35 — xem {@link #runPreview} về lý do {@code public}. */
    public JobOutcome runApply(JobContext context, UUID chartId) {
        PhChart chart = requireChart(chartId);
        List<ScanAnalysisRecompute> drafts = recomputeRepository.findByChartId(chartId);
        Instant now = clock.instant();
        int processed = 0;
        int failed = 0;
        for (ScanAnalysisRecompute draft : drafts) {
            ScanAnalysis original = analysisRepository.findById(draft.getScanAnalysisId()).orElse(null);
            if (original == null || !original.isCurrent()) {
                // Dòng gốc đã bị thay bởi một lượt apply khác (hoặc đã xoá cùng scan). Bỏ qua
                // thay vì ghi thêm một bản "hiện hành" thứ hai — index partial unique trên
                // (scan_id) WHERE is_current sẽ nổ, và nổ ở giữa lô thì lô mất sạch.
                failed++;
                continue;
            }
            ScanAnalysis replacement = copyForRecompute(original, draft, chart, now);

            // Thu tu ba buoc nay la BAT BUOC, khong phai so thich:
            //  (1) tat is_current cua ban goc, (2) DAY xuong DB ngay, (3) moi INSERT ban moi.
            // Hibernate luon phat moi INSERT TRUOC moi UPDATE trong cung mot lan flush, nen neu
            // de ca hai cho den luc commit thi INSERT di truoc va partial unique index
            // uq_scan_analysis_current (scan_id) WHERE is_current no ngay. Quan sat that:
            // "duplicate key value violates unique constraint uq_scan_analysis_current" lam ca
            // lo apply that bai (job_run = FAILED, 0 dong doi).
            original.setCurrent(false, now);
            analysisRepository.save(original);
            scanRepository.flushPendingWrites();
            analysisRepository.save(replacement);

            // Moi cau truy van hien thi JOIN theo scan.current_analysis_id, khong theo
            // scan_analysis.is_current — thieu buoc nay thi "ap dung" chay xong ma nguoi dung
            // van thay con so cu.
            Scan scan = scanRepository.findById(original.getScanId()).orElse(null);
            if (scan != null) {
                scan.pointAtRecomputedAnalysis(replacement.getId(), now);
                scanRepository.save(scan);
            }
            processed++;
        }
        log.info("Backfill apply chart={} runId={} apDung={} boQua={}",
                chartId, context.runId(), processed, failed);
        return JobOutcome.of(processed, 0, failed, null);
    }

    // ------------------------------------------------------------- nội bộ

    /**
     * Đẩy một lượt backfill sang luồng nền.
     *
     * <p><b>Transaction phải mở TƯỜNG MINH bằng {@link TransactionTemplate}, không bằng
     * {@code @Transactional} trên thân job.</b> {@code body.apply(context)} là một lời gọi
     * <i>self-invocation</i> (lambda nắm {@code this}, không nắm proxy), nên annotation sẽ bị
     * bỏ qua lặng lẽ và mỗi lần {@code save} tự mở transaction riêng — mất tính nguyên tử của
     * cả lô, và L35 có thể để lại hai dòng {@code is_current} nửa chừng. Đây là lỗi không hiện
     * ra ở test đơn (test gọi thẳng {@code runPreview}) nên phải chặn bằng thiết kế.</p>
     */
    private void launch(String jobName, Function<JobContext, JobOutcome> body) {
        executor.execute(() -> jobRunner.run(jobName, JobTriggerType.MANUAL, false,
                context -> transactionTemplate.execute(status -> body.apply(context))));
    }

    private PhChart requireChart(UUID chartId) {
        return chartCatalog.findChartById(chartId)
                .orElseThrow(() -> new IllegalStateException("Khong tim thay bang mau " + chartId));
    }

    private Instant windowStart(Period window) {
        Period effective = window == null ? Period.ofDays(90) : window;
        return clock.instant().atOffset(ZoneOffset.UTC).minus(effective).toInstant();
    }

    /** Kết quả tính lại một dòng; {@code null} khi dòng không đủ dữ liệu. */
    private record Recomputed(BigDecimal phValue, ScanClassification classification,
                              BigDecimal confidence, BigDecimal deltaPh, boolean flipped) {
    }

    private Recomputed recompute(ScanAnalysis analysis, PhChart chart, List<PhBandClassifier.Band> bands) {
        if (analysis.getLabL() == null || analysis.getLabA() == null || analysis.getLabB() == null) {
            return null;
        }
        Lab sample = new Lab(analysis.getLabL().doubleValue(),
                analysis.getLabA().doubleValue(),
                analysis.getLabB().doubleValue());
        ChartMatcher.Match match;
        try {
            // onCardPatches = null: không còn ảnh nên không có ô tham chiếu đo được trong ảnh,
            // phải dùng Lab trong DB (nhánh dự phòng mà p6 §6.5.1 S8 đã mô tả).
            match = ChartMatcher.match(sample, chart, null);
        } catch (IllegalArgumentException ex) {
            log.warn("Khong khop duoc dong {} voi bang mau {}: {}",
                    analysis.getId(), chart.id(), ex.getMessage());
            return null;
        }
        BigDecimal newPh = BigDecimal.valueOf(match.phEstimate()).setScale(1, RoundingMode.HALF_UP);
        BigDecimal confidence = cappedConfidence(analysis.getConfidence(), chart);
        BigDecimal halfWidth = ciHalfWidth(analysis);
        PhBandClassifier.Result classified = PhBandClassifier.classify(
                newPh.doubleValue(),
                newPh.subtract(halfWidth).doubleValue(),
                newPh.add(halfWidth).doubleValue(),
                confidence.doubleValue(),
                bands,
                false);
        ScanClassification newClassification = ScanClassification.valueOf(classified.code().name());
        BigDecimal deltaPh = analysis.getPhValue() == null
                ? null
                : newPh.subtract(analysis.getPhValue()).setScale(2, RoundingMode.HALF_UP);
        boolean flipped = newClassification != analysis.getClassification();
        return new Recomputed(newPh, newClassification, confidence, deltaPh, flipped);
    }

    private BigDecimal cappedConfidence(BigDecimal original, PhChart chart) {
        BigDecimal value = original == null ? BigDecimal.ZERO : original;
        return chart.isPlaceholder() ? value.min(PLACEHOLDER_CONFIDENCE_CAP) : value;
    }

    private BigDecimal ciHalfWidth(ScanAnalysis analysis) {
        if (analysis.getPhLow() == null || analysis.getPhHigh() == null) {
            return DEFAULT_CI_HALF_WIDTH;
        }
        return analysis.getPhHigh().subtract(analysis.getPhLow())
                .divide(BigDecimal.TWO, 2, RoundingMode.HALF_UP).abs();
    }

    /**
     * Dòng {@code scan_analysis} mới của L35.
     *
     * <p>Chép NGUYÊN các đại lượng đo được của ảnh (Lab, chất lượng, hiệu chuẩn) vì chúng không
     * phụ thuộc bảng màu; chỉ thay phần kết luận. {@code recompute_of} trỏ về bản gốc để lịch sử
     * truy ngược được (p4 D4 ghi chú nghiệp vụ), và {@code band_id} để {@code null} — giống
     * đường ghi production hiện tại, {@code PhBandClassifier.Band} không mang id dòng
     * {@code ph_classification_band}.</p>
     */
    private ScanAnalysis copyForRecompute(ScanAnalysis original, ScanAnalysisRecompute draft,
                                          PhChart chart, Instant now) {
        ScanAnalysis copy = new ScanAnalysis(uuidV7.generate(), original.getScanId(), now);
        copy.setCurrent(true, now);
        copy.setPhValue(draft.getPhValue());
        BigDecimal halfWidth = ciHalfWidth(original);
        copy.setPhLow(draft.getPhValue() == null ? null : draft.getPhValue().subtract(halfWidth));
        copy.setPhHigh(draft.getPhValue() == null ? null : draft.getPhValue().add(halfWidth));
        copy.setClassification(draft.getClassification() == null
                ? original.getClassification() : draft.getClassification());
        copy.setConfidence(draft.getConfidence() == null ? original.getConfidence() : draft.getConfidence());
        copy.setNearBoundary(original.isNearBoundary());
        copy.setLabL(original.getLabL());
        copy.setLabA(original.getLabA());
        copy.setLabB(original.getLabB());
        copy.setLabSpreadDe00(original.getLabSpreadDe00());
        copy.setBlobCount(original.getBlobCount());
        copy.setIndicatorPixelRatio(original.getIndicatorPixelRatio());
        copy.setSubstrateLabL(original.getSubstrateLabL());
        copy.setSubstrateLabA(original.getSubstrateLabA());
        copy.setSubstrateLabB(original.getSubstrateLabB());
        copy.setDeltaEMin(original.getDeltaEMin());
        copy.setPerpResidualDe00(original.getPerpResidualDe00());
        copy.setMatchPercent(original.getMatchPercent());
        copy.setMatchedSegmentK(original.getMatchedSegmentK());
        copy.setMatchedT(original.getMatchedT());
        copy.setCalibrationMethod(original.getCalibrationMethod());
        copy.setCalibrationResidualDe00(original.getCalibrationResidualDe00());
        copy.setCalibration(original.getCalibration());
        copy.setCardLayoutId(original.getCardLayoutId());
        copy.setQualityMetrics(original.getQualityMetrics());
        copy.setQualityFlags(original.getQualityFlags());
        copy.setChartId(UUID.fromString(chart.id()));
        copy.setChartVersion(chart.version());
        copy.setEngineVersion(original.getEngineVersion());
        copy.setProcessingMs(original.getProcessingMs());
        copy.setRecomputeOf(original.getId());
        return copy;
    }
}
