package com.catcheck.scan.application;

import com.catcheck.scan.api.ChartBackfill;
import com.catcheck.scan.domain.CalibrationMethod;
import com.catcheck.scan.domain.ScanAnalysis;
import com.catcheck.scan.domain.Scan;
import com.catcheck.scan.domain.ScanAnalysisRecompute;
import com.catcheck.scan.domain.ScanClassification;
import com.catcheck.scan.domain.ScanRecomputeImpact;
import com.catcheck.scan.domain.color.ChartCatalog;
import com.catcheck.scan.domain.color.DeltaE2000Params;
import com.catcheck.scan.domain.color.Lab;
import com.catcheck.scan.domain.color.PhBandClassifier;
import com.catcheck.scan.domain.color.PhChart;
import com.catcheck.scan.domain.color.PhChartPoint;
import com.catcheck.scan.domain.port.ScanAnalysisRecomputeRepository;
import com.catcheck.scan.domain.port.ScanAnalysisRepository;
import com.catcheck.scan.domain.port.ScanRepository;
import com.catcheck.shared.id.UuidV7;
import com.catcheck.shared.job.JobProperties;
import com.catcheck.shared.job.JobRunStatus;
import com.catcheck.shared.job.application.JobRunRecorder;
import com.catcheck.shared.job.application.JobRunner;
import com.catcheck.shared.testing.NoOpTransactionManager;
import com.catcheck.shared.testing.RecordingJobRunPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L33–L35 — backfill bảng màu (p8 §8.4.12 mục (c), p6 §6.5.4).
 *
 * <p>Điều quan trọng nhất ở đây: <b>preview không được chạm vào kết quả đang hiển thị</b>, và
 * apply phải đổi {@code is_current} theo cặp (bật bản mới, tắt bản cũ) chứ không để lại hai bản
 * hiện hành — index partial unique của {@code scan_analysis} sẽ chặn, nhưng chặn giữa lô thì cả
 * lô mất.</p>
 */
class ChartBackfillServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-06T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID CHART_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    private final InMemoryAnalyses analyses = new InMemoryAnalyses();
    private final InMemoryScans scans = new InMemoryScans();
    private final InMemoryRecomputes recomputes = new InMemoryRecomputes();
    private final RecordingJobRunPort jobRuns = new RecordingJobRunPort();

    private ChartBackfillService service(boolean placeholder) {
        JobRunner runner = new JobRunner(
                new JobRunRecorder(jobRuns, new UuidV7(CLOCK), CLOCK),
                jobProperties(),
                new SimpleMeterRegistry(),
                CLOCK);
        return new ChartBackfillService(
                analyses,
                scans,
                recomputes,
                new FixedCatalog(placeholder),
                runner,
                Runnable::run,
                new UuidV7(CLOCK),
                CLOCK,
                new NoOpTransactionManager());
    }

    @Test
    void previewWritesRecomputeRowsAndLeavesCurrentResultsUntouched() {
        ScanAnalysis original = analysis(5.6, ScanClassification.LOW, new Lab(70.0, 20.0, 30.0));

        service(false).startPreview(CHART_ID, Period.ofDays(90));

        assertEquals(1, recomputes.rows.size(), "L33 phai ghi dung mot dong scan_analysis_recompute");
        ScanAnalysisRecompute row = recomputes.rows.getFirst();
        assertEquals(original.getId(), row.getScanAnalysisId());
        assertEquals(CHART_ID, row.getChartId());
        // Preview la ban NHAP: khong duoc tao dong scan_analysis nao, khong duoc tat is_current.
        assertEquals(1, analyses.store.size(), "L33 khong duoc tao dong scan_analysis moi");
        assertTrue(analyses.store.get(original.getId()).isCurrent(),
                "L33 khong duoc tat is_current cua ban goc");
        RecordingJobRunPort.Row jobRun = jobRuns.only();
        assertEquals(ChartBackfillService.PREVIEW_JOB, jobRun.jobName());
        assertEquals(JobRunStatus.SUCCESS, jobRun.status());
        assertEquals(1, jobRun.itemsProcessed());
    }

    @Test
    void previewFlagsFlippedClassificationAndDeltaPh() {
        // Lab nay khop muc pH 8.0 cua bang mau test; ban goc dang luu 5.6/LOW nen phan loai lat.
        analysis(5.6, ScanClassification.LOW, new Lab(50.0, -10.0, -20.0));

        service(false).startPreview(CHART_ID, Period.ofDays(90));

        ScanAnalysisRecompute row = recomputes.rows.getFirst();
        assertTrue(row.isFlippedClassification(), "pH 5.6 -> 8.0 phai lat phan loai");
        assertNotEquals(ScanClassification.LOW, row.getClassification());
        assertTrue(row.getDeltaPh().compareTo(BigDecimal.ZERO) > 0, "deltaPh phai duong khi pH tang");
        ChartBackfill.BackfillImpact impact = service(false).impact(CHART_ID);
        assertEquals(1L, impact.evaluated());
        assertEquals(1L, impact.flipped());
        assertEquals(1, impact.samples().size());
    }

    @Test
    void previewReplacesTheEarlierDraftInsteadOfAccumulating() {
        analysis(5.6, ScanClassification.LOW, new Lab(70.0, 20.0, 30.0));

        service(false).startPreview(CHART_ID, Period.ofDays(90));
        service(false).startPreview(CHART_ID, Period.ofDays(90));

        assertEquals(1, recomputes.rows.size(),
                "Hai lan bam 'xem truoc' khong duoc cong don — L34 se dem gap doi");
        assertEquals(1L, service(false).impact(CHART_ID).evaluated());
    }

    @Test
    void placeholderChartCapsConfidenceAtSixTenths() {
        analysis(5.6, ScanClassification.LOW, new Lab(70.0, 20.0, 30.0));

        service(true).startPreview(CHART_ID, Period.ofDays(90));

        assertEquals(0, recomputes.rows.getFirst().getConfidence().compareTo(new BigDecimal("0.600")),
                "p6 §6.6.6: bang mau is_placeholder phai cap confidence o 0.60");
    }

    @Test
    void rowsOutsideTheWindowAreNotEvaluated() {
        ScanAnalysis old = analysis(5.6, ScanClassification.LOW, new Lab(70.0, 20.0, 30.0));
        analyses.computedAt.put(old.getId(), NOW.minus(Duration.ofDays(200)));

        service(false).startPreview(CHART_ID, Period.ofDays(90));

        assertTrue(recomputes.rows.isEmpty(), "Dong ngoai cua so P90D khong duoc tinh lai");
    }

    @Test
    void rowsWithoutLabAreNotEvaluated() {
        analysis(5.6, ScanClassification.LOW, null);

        service(false).startPreview(CHART_ID, Period.ofDays(90));

        assertTrue(recomputes.rows.isEmpty(),
                "Khong co Lab thi khong tinh lai duoc (pH nhap tay) — phai bi loai, khong duoc bo qua lang le");
    }

    @Test
    void applyCreatesANewCurrentAnalysisPointingBackAtTheOriginal() {
        ScanAnalysis original = analysis(5.6, ScanClassification.LOW, new Lab(50.0, -10.0, -20.0));
        ChartBackfillService service = service(false);
        service.startPreview(CHART_ID, Period.ofDays(90));

        service.startApply(CHART_ID);

        assertEquals(2, analyses.store.size(), "L35 phai TAO dong moi, khong sua dong cu");
        assertFalse(analyses.store.get(original.getId()).isCurrent(), "Ban goc phai thoi hien hanh");
        ScanAnalysis replacement = analyses.store.values().stream()
                .filter(ScanAnalysis::isCurrent)
                .findFirst()
                .orElseThrow();
        assertEquals(original.getId(), replacement.getRecomputeOf(),
                "p4 D4: dong moi phai tro ve ban goc qua recompute_of");
        assertEquals(CHART_ID, replacement.getChartId());
        assertEquals(original.getLabL(), replacement.getLabL(), "Lab la do luong cua anh — phai chep nguyen");
        assertNotEquals(original.getPhValue(), replacement.getPhValue());
        assertEquals(2, jobRuns.rows().size(), "preview + apply = hai dong job_run");
        Scan scan = scans.store.get(original.getScanId());
        assertEquals(replacement.getId(), scan.getCurrentAnalysisId(),
                "Moi cau truy van hien thi JOIN theo scan.current_analysis_id — thieu buoc nay thi"
                        + " 'ap dung' chay xong ma nguoi dung van thay con so cu");
        assertTrue(analyses.flushes > 0,
                "Phai flush UPDATE is_current=false TRUOC khi INSERT ban moi, neu khong partial"
                        + " unique index uq_scan_analysis_current no");
    }

    @Test
    void applySkipsRowsWhoseOriginalIsNoLongerCurrent() {
        ScanAnalysis original = analysis(5.6, ScanClassification.LOW, new Lab(50.0, -10.0, -20.0));
        ChartBackfillService service = service(false);
        service.startPreview(CHART_ID, Period.ofDays(90));
        original.setCurrent(false, NOW);

        service.startApply(CHART_ID);

        assertEquals(1, analyses.store.size(),
                "Ban goc da bi thay boi luot apply khac — khong duoc tao ban hien hanh thu hai");
        RecordingJobRunPort.Row applyRun = jobRuns.rows().getLast();
        assertEquals(JobRunStatus.PARTIAL, applyRun.status(),
                "Dong bi bo qua phai dem vao items_failed, keo trang thai ve PARTIAL");
        assertEquals(1, applyRun.itemsFailed());
    }

    @Test
    void impactIsEmptyWhenNoPreviewHasRun() {
        ChartBackfill.BackfillImpact impact = service(false).impact(CHART_ID);

        assertEquals(0L, impact.evaluated());
        assertEquals(0L, impact.flipped());
        assertNull(impact.maxAbsDeltaPh());
        assertTrue(impact.samples().isEmpty());
    }

    // ------------------------------------------------------------------ fixtures

    private ScanAnalysis analysis(double ph, ScanClassification classification, Lab lab) {
        UUID scanId = UUID.randomUUID();
        scans.store.put(scanId, new Scan(scanId, UUID.randomUUID(), null,
                com.catcheck.scan.domain.ScanAssignment.UNASSIGNED, NOW,
                com.catcheck.scan.domain.CaptureSource.CAMERA, null, false, false,
                com.catcheck.scan.domain.StoreImageReason.CONSENT_OFF,
                com.catcheck.scan.domain.ScanStatus.ANALYZED, null, "idem-" + scanId, NOW));
        ScanAnalysis analysis = new ScanAnalysis(UUID.randomUUID(), scanId, NOW);
        analysis.setCurrent(true, NOW);
        analysis.setPhValue(BigDecimal.valueOf(ph));
        analysis.setPhLow(BigDecimal.valueOf(ph - 0.2));
        analysis.setPhHigh(BigDecimal.valueOf(ph + 0.2));
        analysis.setClassification(classification);
        analysis.setConfidence(new BigDecimal("0.900"));
        analysis.setCalibrationMethod(CalibrationMethod.CARD_CCM);
        analysis.setEngineVersion(PipelineOutcome.ENGINE_VERSION);
        if (lab != null) {
            analysis.setLabL(BigDecimal.valueOf(lab.l()));
            analysis.setLabA(BigDecimal.valueOf(lab.a()));
            analysis.setLabB(BigDecimal.valueOf(lab.b()));
        }
        analyses.store.put(analysis.getId(), analysis);
        analyses.computedAt.put(analysis.getId(), NOW);
        return analysis;
    }

    private static JobProperties jobProperties() {
        JobProperties.JobSetting setting = new JobProperties.JobSetting(
                true, "0 5 * * * *", Duration.ofMinutes(50), 500, 5_000, false);
        return new JobProperties(true, ZoneId.of("Asia/Ho_Chi_Minh"),
                setting, setting, setting, setting, setting, setting, setting, setting);
    }

    /** Bảng màu hai mức (5.5 và 8.0) — đủ để {@code ChartMatcher} nội suy. */
    private static final class FixedCatalog implements ChartCatalog {

        private final boolean placeholder;

        private FixedCatalog(boolean placeholder) {
            this.placeholder = placeholder;
        }

        @Override
        public Optional<PhChart> findActiveChart(String productLine, String productionBatch) {
            return findChartById(CHART_ID);
        }

        @Override
        public Optional<PhChart> findChartById(UUID chartId) {
            List<PhChartPoint> points = List.of(
                    new PhChartPoint("00000000-0000-0000-0000-000000000101", 5.5,
                            new Lab(70.0, 20.0, 30.0), 4.0, "#AABBCC", "#AABBCC", "5,5", "5.5", 0, 0.0),
                    new PhChartPoint("00000000-0000-0000-0000-000000000102", 8.0,
                            new Lab(50.0, -10.0, -20.0), 4.0, "#112233", "#112233", "8,0", "8.0", 0, 0.0));
            return Optional.of(new PhChart(chartId.toString(), 2, PhChart.Status.ACTIVE,
                    "D65", 2, placeholder, points, DeltaE2000Params.CAT_CHECK));
        }

        @Override
        public List<PhBandClassifier.Band> findGlobalBands() {
            return List.of(
                    band(PhBandClassifier.Code.LOW, null, 6.0, true, false, PhBandClassifier.Severity.WATCH, 1),
                    band(PhBandClassifier.Code.SLIGHTLY_LOW, 6.0, 6.3, true, false, PhBandClassifier.Severity.ATTENTION, 2),
                    band(PhBandClassifier.Code.IN_RANGE, 6.3, 6.6, true, true, PhBandClassifier.Severity.NORMAL, 3),
                    band(PhBandClassifier.Code.SLIGHTLY_HIGH, 6.6, 7.0, false, true, PhBandClassifier.Severity.ATTENTION, 4),
                    band(PhBandClassifier.Code.HIGH, 7.0, null, false, true, PhBandClassifier.Severity.WATCH, 5),
                    band(PhBandClassifier.Code.INCONCLUSIVE, null, null, true, true, PhBandClassifier.Severity.NEUTRAL, 6));
        }

        private static PhBandClassifier.Band band(PhBandClassifier.Code code, Double min, Double max,
                                                  boolean minInclusive, boolean maxInclusive,
                                                  PhBandClassifier.Severity severity, int sortOrder) {
            return new PhBandClassifier.Band(code, min, max, minInclusive, maxInclusive,
                    code.name(), severity, "color-ph-" + sortOrder, "icon", sortOrder);
        }
    }

    /**
     * {@code scan_analysis} trong bộ nhớ. {@code computedAt} giữ riêng vì entity không có setter
     * cho cột đó (nó là {@code updatable = false}).
     */
    private static final class InMemoryAnalyses implements ScanAnalysisRepository {

        private final Map<UUID, ScanAnalysis> store = new LinkedHashMap<>();
        private final Map<UUID, Instant> computedAt = new LinkedHashMap<>();
        private int flushes;

        @Override
        public ScanAnalysis save(ScanAnalysis analysis) {
            store.put(analysis.getId(), analysis);
            computedAt.putIfAbsent(analysis.getId(), NOW);
            return analysis;
        }

        @Override
        public Optional<ScanAnalysis> findCurrentByScanId(UUID scanId) {
            return store.values().stream()
                    .filter(a -> a.getScanId().equals(scanId) && a.isCurrent())
                    .findFirst();
        }

        @Override
        public List<ScanAnalysis> findByScanIdOrderByComputedAtDesc(UUID scanId) {
            return store.values().stream().filter(a -> a.getScanId().equals(scanId)).toList();
        }

        @Override
        public Optional<ScanAnalysis> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<ScanAnalysis> findBackfillCandidates(Instant computedFrom, String engineVersionPrefix,
                                                         int limit, int offset) {
            List<ScanAnalysis> matching = candidates(computedFrom, engineVersionPrefix);
            return matching.stream().skip(offset).limit(limit).toList();
        }

        @Override
        public long countBackfillCandidates(Instant computedFrom, String engineVersionPrefix) {
            return candidates(computedFrom, engineVersionPrefix).size();
        }

        private List<ScanAnalysis> candidates(Instant computedFrom, String engineVersionPrefix) {
            List<ScanAnalysis> matching = new ArrayList<>();
            for (ScanAnalysis a : store.values()) {
                boolean hasLab = a.getLabL() != null && a.getLabA() != null && a.getLabB() != null;
                boolean inWindow = !computedAt.get(a.getId()).isBefore(computedFrom);
                boolean sameMajor = a.getEngineVersion() != null
                        && a.getEngineVersion().startsWith(engineVersionPrefix);
                if (a.isCurrent() && hasLab && inWindow && sameMajor) {
                    matching.add(a);
                }
            }
            matching.sort(Comparator.comparing(ScanAnalysis::getId));
            return matching;
        }
    }

    /**
     * {@code scan} trong bộ nhớ. {@code flushPendingWrites} chỉ đếm số lần gọi: điều cần kiểm là
     * L35 có đẩy lệnh {@code UPDATE is_current = false} xuống DB trước khi INSERT bản mới hay
     * không — fake không có constraint nên không tự nổ như Postgres.
     */
    private final class InMemoryScans implements ScanRepository {

        private final Map<UUID, Scan> store = new LinkedHashMap<>();

        @Override
        public Scan save(Scan scan) {
            store.put(scan.getId(), scan);
            return scan;
        }

        @Override
        public Optional<Scan> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<Scan> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey) {
            return Optional.empty();
        }

        @Override
        public void flushPendingWrites() {
            analyses.flushes++;
        }
    }

    /** {@code scan_analysis_recompute} trong bộ nhớ. */
    private static final class InMemoryRecomputes implements ScanAnalysisRecomputeRepository {

        private final List<ScanAnalysisRecompute> rows = new ArrayList<>();

        @Override
        public void saveAll(List<ScanAnalysisRecompute> newRows) {
            rows.addAll(newRows);
        }

        @Override
        public int deleteByChartId(UUID chartId) {
            int before = rows.size();
            rows.removeIf(row -> row.getChartId().equals(chartId));
            return before - rows.size();
        }

        @Override
        public ScanRecomputeImpact impactByChartId(UUID chartId) {
            List<ScanAnalysisRecompute> matching = findByChartId(chartId);
            if (matching.isEmpty()) {
                return ScanRecomputeImpact.empty();
            }
            long flipped = matching.stream().filter(ScanAnalysisRecompute::isFlippedClassification).count();
            BigDecimal max = matching.stream()
                    .map(ScanAnalysisRecompute::getDeltaPh)
                    .filter(java.util.Objects::nonNull)
                    .map(BigDecimal::abs)
                    .max(Comparator.naturalOrder())
                    .orElse(null);
            return new ScanRecomputeImpact(matching.size(), flipped, max,
                    findLatestJobIdByChartId(chartId).orElse(null));
        }

        @Override
        public List<ScanAnalysisRecompute> findTopByChartIdOrderByAbsDeltaPhDesc(UUID chartId, int limit) {
            return findByChartId(chartId).stream().limit(limit).toList();
        }

        @Override
        public List<ScanAnalysisRecompute> findByChartId(UUID chartId) {
            return rows.stream().filter(row -> row.getChartId().equals(chartId)).toList();
        }

        @Override
        public Optional<UUID> findLatestJobIdByChartId(UUID chartId) {
            return findByChartId(chartId).stream()
                    .map(ScanAnalysisRecompute::getJobId)
                    .filter(java.util.Objects::nonNull)
                    .findFirst();
        }
    }
}
