package com.catcheck.scan.application;

import com.catcheck.credit.api.CreditConsumption;
import com.catcheck.insight.application.RuleEvaluationService;
import com.catcheck.insight.domain.HealthFlag;
import com.catcheck.insight.domain.MonitoringRule;
import com.catcheck.insight.domain.MonitoringRuleCode;
import com.catcheck.insight.domain.MonitoringRuleFixtures;
import com.catcheck.insight.domain.port.HealthFlagRepository;
import com.catcheck.insight.domain.port.MonitoringRuleRepository;
import com.catcheck.scan.api.ScanHistoryQuery;
import com.catcheck.scan.api.ScanSavedEvent;
import com.catcheck.scan.application.spi.OnboardingProgressPort;
import com.catcheck.scan.domain.CaptureSource;
import com.catcheck.scan.domain.Scan;
import com.catcheck.scan.domain.ScanAnalysis;
import com.catcheck.scan.domain.ScanAssignment;
import com.catcheck.scan.domain.ScanImage;
import com.catcheck.scan.domain.ScanStatus;
import com.catcheck.scan.domain.color.ChartCatalog;
import com.catcheck.scan.domain.color.DeltaE2000Params;
import com.catcheck.scan.domain.color.Lab;
import com.catcheck.scan.domain.color.PhBandClassifier;
import com.catcheck.scan.domain.color.PhChart;
import com.catcheck.scan.domain.color.PhChartPoint;
import com.catcheck.scan.domain.port.CatOwnershipPort;
import com.catcheck.scan.domain.port.ScanAnalysisRepository;
import com.catcheck.scan.domain.port.ScanImageRepository;
import com.catcheck.scan.domain.port.ScanRepository;
import com.catcheck.shared.id.UuidV7;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ScanSavedEventRuleVisibilityTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID CAT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final Instant T0 = Instant.parse("2026-10-03T02:00:00Z");

    private static final BigDecimal LOW_CONFIDENCE = new BigDecimal("0.450");

    private final FakeScanStore store = new FakeScanStore();
    private final RecordingHealthFlagRepository healthFlags = new RecordingHealthFlagRepository();

    @Test
    @DisplayName("H15.75 — scan thứ 2 confidence thấp sinh LOW_QUALITY_STREAK ngay, không cần scan thứ 3")
    void secondLowConfidenceScanRaisesTheFlagImmediately() {
        persistScan(T0, LOW_CONFIDENCE);
        assertThat(healthFlags.saved)
                .as("mot lan quet chua du streak = 2")
                .isEmpty();

        persistScan(T0.plus(Duration.ofHours(6)), LOW_CONFIDENCE);

        assertThat(healthFlags.saved)
                .as("flag phai no o CHINH scan thu 2 (H15.75), khong doi scan thu 3")
                .hasSize(1);
        HealthFlag flag = healthFlags.saved.getFirst();
        assertThat(flag.getRuleCode()).isEqualTo(MonitoringRuleCode.LOW_QUALITY_STREAK.name());
        assertThat(flag.getCatId()).isEqualTo(CAT_ID);
        assertThat(flag.getTriggerScanId())
                .as("trigger_scan_id tro vao dung scan vua tao")
                .isEqualTo(store.lastScanId);
    }

    @Test
    @DisplayName("Fake không 'rộng tay' hơn DB thật — hàng chưa flush thì truy vấn không thấy")
    void theFakeOnlyExposesFlushedRows() {
        UUID scanId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        Scan scan = new Scan(scanId, USER_ID, CAT_ID, ScanAssignment.ASSIGNED, T0,
                CaptureSource.CAMERA, null, true, false, null, ScanStatus.PENDING, null, "k", T0);
        ScanAnalysis analysis = new ScanAnalysis(analysisId, scanId, T0);
        analysis.setConfidence(LOW_CONFIDENCE);
        scan.markAnalyzed(analysisId, null, T0);
        store.save(scan);
        store.saveAnalysis(analysis);

        assertThat(store.recentValidScans(CAT_ID, T0.minus(Duration.ofDays(1)), 10))
                .as("chua flush thi SQL tho khong thay hang nao")
                .isEmpty();

        store.flushPendingWrites();

        assertThat(store.recentValidScans(CAT_ID, T0.minus(Duration.ofDays(1)), 10))
                .extracting(ScanHistoryQuery.RecentScan::scanId)
                .containsExactly(scanId);
    }


    private void persistScan(Instant capturedAt, BigDecimal confidence) {
        Clock clock = Clock.fixed(capturedAt, ZoneOffset.UTC);
        RuleEvaluationService rules = new RuleEvaluationService(
                new FixedRuleRepository(capturedAt), healthFlags, store, new UuidV7(clock), clock);

        ScanPersistenceService service = new ScanPersistenceService(
                store, store.analyses(), store, new TrialOnlyCreditConsumption(), event -> { },
                syncPublisherTo(rules), userId -> { }, new UuidV7(clock), clock);

        PipelineOutcome outcome = new ManualPipelineOutcomeFactory(new FixedChartCatalog())
                .fromManualPh(new BigDecimal("6.45"), null, confidence, null);

        service.persist(
                new SubmitScanCommand(USER_ID, CAT_ID, ScanAssignment.ASSIGNED, capturedAt,
                        CaptureSource.CAMERA, null, "idem-" + capturedAt, null, null, new byte[] {1}),
                outcome,
                new CatOwnershipPort.CatSnapshot(CAT_ID, USER_ID, "Miu", false, false),
                true, false, false, Optional.empty());
    }

    private static ApplicationEventPublisher syncPublisherTo(RuleEvaluationService rules) {
        return event -> {
            if (event instanceof ScanSavedEvent saved) {
                rules.onScanSaved(saved);
            }
        };
    }


    private static final class FakeScanStore
            implements ScanRepository, ScanImageRepository, ScanHistoryQuery {

        private final Map<UUID, Scan> pendingScans = new LinkedHashMap<>();
        private final Map<UUID, ScanAnalysis> pendingAnalyses = new LinkedHashMap<>();
        private final Map<UUID, Scan> visibleScans = new LinkedHashMap<>();
        private final Map<UUID, ScanAnalysis> visibleAnalyses = new LinkedHashMap<>();
        private UUID lastScanId;

        @Override
        public Scan save(Scan scan) {
            pendingScans.put(scan.getId(), scan);
            lastScanId = scan.getId();
            return scan;
        }

        @Override
        public ScanImage save(ScanImage image) {
            return image;
        }

        @Override
        public void flushPendingWrites() {
            visibleScans.putAll(pendingScans);
            visibleAnalyses.putAll(pendingAnalyses);
            pendingScans.clear();
            pendingAnalyses.clear();
        }

        @Override
        public List<RecentScan> recentValidScans(UUID catId, Instant since, int limit) {
            return visibleScans.values().stream()
                    .filter(s -> catId.equals(s.getCatId()))
                    .filter(s -> s.getAssignment() == ScanAssignment.ASSIGNED)
                    .filter(s -> s.getStatus() == ScanStatus.ANALYZED)
                    .filter(s -> s.getCurrentAnalysisId() != null)
                    .filter(s -> !s.getCapturedAt().isBefore(since))
                    .filter(s -> visibleAnalyses.containsKey(s.getCurrentAnalysisId()))
                    .sorted(Comparator.comparing(Scan::getCapturedAt).reversed())
                    .limit(limit)
                    .map(s -> {
                        ScanAnalysis a = visibleAnalyses.get(s.getCurrentAnalysisId());
                        return new RecentScan(s.getId(), s.getCapturedAt(),
                                a.getClassification() == null ? null : a.getClassification().name(),
                                a.getPhValue(), a.getConfidence(), a.isNearBoundary(),
                                a.getCalibrationMethod() == null ? null : a.getCalibrationMethod().name());
                    })
                    .toList();
        }

        @Override
        public List<ExportScanRow> scansForExport(UUID catId, Instant from, Instant to) {
            return List.of();
        }

        @Override
        public Optional<Scan> findById(UUID id) {
            return Optional.ofNullable(visibleScans.getOrDefault(id, pendingScans.get(id)));
        }

        @Override
        public Optional<Scan> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey) {
            return Optional.empty();
        }

        @Override
        public Optional<ScanImage> findByScanId(UUID scanId) {
            return Optional.empty();
        }

        ScanAnalysisRepository analyses() {
            return new ScanAnalysisRepository() {

                @Override
                public ScanAnalysis save(ScanAnalysis analysis) {
                    pendingAnalyses.put(analysis.getId(), analysis);
                    return analysis;
                }

                @Override
                public Optional<ScanAnalysis> findCurrentByScanId(UUID scanId) {
                    return Optional.empty();
                }

                @Override
                public List<ScanAnalysis> findByScanIdOrderByComputedAtDesc(UUID scanId) {
                    return List.of();
                }

                @Override
                public List<ScanAnalysis> findBackfillCandidates(UUID chartId, Instant computedFrom,
                                                                 String engineVersionPrefix,
                                                                 int limit, int offset) {
                    return List.of();
                }

                @Override
                public long countBackfillCandidates(UUID chartId, Instant computedFrom,
                                                    String engineVersionPrefix) {
                    return 0L;
                }

                @Override
                public Optional<ScanAnalysis> findById(UUID id) {
                    return Optional.ofNullable(visibleAnalyses.getOrDefault(id, pendingAnalyses.get(id)));
                }
            };
        }

        void saveAnalysis(ScanAnalysis analysis) {
            analyses().save(analysis);
        }
    }

    private static final class RecordingHealthFlagRepository implements HealthFlagRepository {

        private final List<HealthFlag> saved = new ArrayList<>();

        @Override
        public Optional<HealthFlag> saveIfNotDuplicate(HealthFlag flag) {
            boolean duplicate = saved.stream()
                    .anyMatch(existing -> existing.getDedupeKey().equals(flag.getDedupeKey()));
            if (duplicate) {
                return Optional.empty();
            }
            saved.add(flag);
            return Optional.of(flag);
        }

        @Override
        public Optional<HealthFlag> findById(UUID id) {
            return Optional.empty();
        }

        @Override
        public Page findByFilter(UUID userId, UUID catId, Boolean acknowledged, String severity,
                                  String cursor, int limit) {
            return new Page(List.of(), null);
        }

        @Override
        public HealthFlag save(HealthFlag flag) {
            saved.add(flag);
            return flag;
        }

        @Override
        public List<UUID> deleteByTriggerScanId(UUID scanId) {
            return List.of();
        }

        @Override
        public boolean isCatOwnedByUser(UUID catId, UUID userId) {
            return true;
        }

        @Override
        public List<HealthFlag> findByCatIdAndTriggeredAtBetween(UUID catId, Instant from, Instant to) {
            return List.of();
        }
    }

    private record FixedRuleRepository(Instant now) implements MonitoringRuleRepository {

        @Override
        public List<MonitoringRule> findAllEnabled() {
            return List.of(MonitoringRuleFixtures.lowQualityStreak(2, 0.50, now));
        }

        @Override
        public List<MonitoringRule> findAllEnabledForDisplay() {
            return findAllEnabled();
        }

        @Override
        public Optional<MonitoringRule> findByCode(String code) {
            return MonitoringRuleCode.LOW_QUALITY_STREAK.name().equals(code)
                    ? Optional.of(MonitoringRuleFixtures.lowQualityStreak(2, 0.50, now))
                    : Optional.empty();
        }

        @Override
        public List<MonitoringRule> findAll() {
            return findAllEnabled();
        }

        @Override
        public List<MonitoringRule> findAllForAdmin() {
            return findAllEnabled();
        }

        @Override
        public MonitoringRule saveAndReload(MonitoringRule rule) {
            return rule;
        }
    }

    private static final class TrialOnlyCreditConsumption implements CreditConsumption {

        @Override
        public CreditCharge consume(UUID userId, CreditConsumeCommand command) {
            throw new UnsupportedOperationException("test chi chay nhanh trial");
        }

        @Override
        public CreditRefund refund(UUID userId, CreditRefundCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasAvailableCredit(UUID userId) {
            return false;
        }

        @Override
        public boolean hasTrialScanRemaining(UUID userId) {
            return true;
        }

        @Override
        public boolean consumeTrialScan(UUID userId) {
            return true;
        }
    }

    private static final class FixedChartCatalog implements ChartCatalog {

        @Override
        public Optional<PhChart> findActiveChart(String productLine, String productionBatch) {
            List<PhChartPoint> points = List.of(
                    new PhChartPoint("00000000-0000-0000-0000-000000000101", 5.5,
                            new Lab(70.0, 20.0, 30.0), 2.0, "#AABBCC", "#AABBCC", "5,5", "5.5", 0, 0.0),
                    new PhChartPoint("00000000-0000-0000-0000-000000000102", 8.0,
                            new Lab(50.0, -10.0, -20.0), 2.0, "#112233", "#112233", "8,0", "8.0", 0, 0.0));
            return Optional.of(new PhChart("00000000-0000-0000-0000-000000000002", 1,
                    PhChart.Status.ACTIVE, "D65", 2, true, points, DeltaE2000Params.CAT_CHECK));
        }

        @Override
        public Optional<PhChart> findChartById(java.util.UUID chartId) {
            return findActiveChart("STANDARD", null);
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

        private PhBandClassifier.Band band(PhBandClassifier.Code code, Double min, Double max,
                                           boolean minInclusive, boolean maxInclusive,
                                           PhBandClassifier.Severity severity, int sortOrder) {
            return new PhBandClassifier.Band(code, min, max, minInclusive, maxInclusive,
                    code.name(), severity, "color-ph-unknown", "help-circle", sortOrder);
        }
    }

}
