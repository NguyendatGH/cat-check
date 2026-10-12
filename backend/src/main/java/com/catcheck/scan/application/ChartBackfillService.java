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

@Service
public class ChartBackfillService implements ChartBackfill {

    private static final Logger log = LoggerFactory.getLogger(ChartBackfillService.class);

    public static final String PREVIEW_JOB = "chart-backfill-preview";

    public static final String APPLY_JOB = "chart-backfill-apply";

    private static final String ENGINE_MAJOR_PREFIX =
            PipelineOutcome.ENGINE_VERSION.substring(0, PipelineOutcome.ENGINE_VERSION.indexOf('.') + 1);

    private static final BigDecimal PLACEHOLDER_CONFIDENCE_CAP = new BigDecimal("0.600");

    private static final BigDecimal DEFAULT_CI_HALF_WIDTH = new BigDecimal("0.2");

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


    @Override
    public void startPreview(UUID chartId, Period window) {
        launch(PREVIEW_JOB, context -> runPreview(context, chartId, window));
    }


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


    @Override
    public void startApply(UUID chartId) {
        launch(APPLY_JOB, context -> runApply(context, chartId));
    }


    public JobOutcome runPreview(JobContext context, UUID chartId, Period window) {
        PhChart chart = requireChart(chartId);
        List<PhBandClassifier.Band> bands = chartCatalog.findGlobalBands();
        Instant from = windowStart(window);

        int removed = recomputeRepository.deleteByChartId(chartId);
        long total = analysisRepository.countBackfillCandidates(chartId, from, ENGINE_MAJOR_PREFIX);
        log.info("Backfill preview chart={} window={} ungVien={} xoaNhapCu={}",
                chartId, window, total, removed);

        int processed = 0;
        int failed = 0;
        for (int offset = 0; offset < total; offset += BATCH_SIZE) {
            List<ScanAnalysis> batch =
                    analysisRepository.findBackfillCandidates(chartId, from, ENGINE_MAJOR_PREFIX, BATCH_SIZE, offset);
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

    public JobOutcome runApply(JobContext context, UUID chartId) {
        PhChart chart = requireChart(chartId);
        List<ScanAnalysisRecompute> drafts = recomputeRepository.findByChartId(chartId);
        Instant now = clock.instant();
        int processed = 0;
        int failed = 0;
        for (ScanAnalysisRecompute draft : drafts) {
            ScanAnalysis original = analysisRepository.findById(draft.getScanAnalysisId()).orElse(null);
            if (original == null || !original.isCurrent()) {
                failed++;
                continue;
            }
            ScanAnalysis replacement = copyForRecompute(original, draft, chart, now);

            original.setCurrent(false, now);
            analysisRepository.save(original);
            scanRepository.flushPendingWrites();
            analysisRepository.save(replacement);

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
