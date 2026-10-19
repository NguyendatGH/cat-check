package com.catcheck.insight.application;

import com.catcheck.insight.domain.HealthFlag;
import com.catcheck.insight.domain.HealthFlagSeverity;
import com.catcheck.insight.domain.MonitoringRule;
import com.catcheck.insight.domain.MonitoringRuleCode;
import com.catcheck.insight.domain.MonitoringRuleFixtures;
import com.catcheck.insight.domain.port.HealthFlagRepository;
import com.catcheck.insight.domain.port.MonitoringRuleRepository;
import com.catcheck.scan.api.ScanHistoryQuery;
import com.catcheck.scan.api.ScanSavedEvent;
import com.catcheck.shared.id.UuidV7;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RuleEvaluationServiceTest {

    private static final UUID CAT_ID = UUID.randomUUID();
    private static final Instant T0 = Instant.parse("2026-10-09T23:59:00Z");

    private final FakeScans scans = new FakeScans();
    private final FakeFlags flags = new FakeFlags();


    @Test
    void cooldownHoldsAcrossTheDedupeBucketBoundary() {
        MonitoringRule r4 = MonitoringRuleFixtures.lowQualityStreak(2, 0.50, T0);
        scans.add(T0.minusSeconds(600), "NORMAL", "6.4", "0.40", "CARD_CCM");
        ScanHistoryQuery.RecentScan second = scans.add(T0, "NORMAL", "6.4", "0.40", "CARD_CCM");
        service(r4, T0).onScanSaved(event(second));
        assertThat(flags.saved).hasSize(1);

        Instant later = T0.plusSeconds(120);
        ScanHistoryQuery.RecentScan third = scans.add(later, "NORMAL", "6.4", "0.40", "CARD_CCM");
        service(r4, later).onScanSaved(event(third));

        assertThat(flags.saved).as("cooldown 24h vẫn còn hiệu lực").hasSize(1);
    }


    @Test
    void windowToStaysAfterWindowFromWhenClientClockRunsAhead() {
        MonitoringRule r4 = MonitoringRuleFixtures.lowQualityStreak(2, 0.50, T0);
        scans.add(T0.plusSeconds(300), "NORMAL", "6.4", "0.40", "CARD_CCM");
        ScanHistoryQuery.RecentScan second = scans.add(T0.plusSeconds(420), "NORMAL", "6.4", "0.40", "CARD_CCM");

        service(r4, T0).onScanSaved(event(second));

        HealthFlag flag = flags.saved.getFirst();
        assertThat(flag.getWindowTo()).as("ck_health_flag_window").isAfter(flag.getWindowFrom());
    }


    @Test
    void repeatedOutOfRangeDoesNotClaimOneDirectionWhenBothSidesReachTheThreshold() {
        scans.add(T0.minusSeconds(3 * 3600), "LOW", "5.8", "0.80", "CARD_CCM");
        scans.add(T0.minusSeconds(2 * 3600), "HIGH", "7.2", "0.80", "CARD_CCM");
        scans.add(T0.minusSeconds(3600), "SLIGHTLY_LOW", "6.1", "0.80", "CARD_CCM");
        ScanHistoryQuery.RecentScan trigger = scans.add(T0, "SLIGHTLY_HIGH", "6.8", "0.80", "CARD_CCM");

        service(repeatedRule(), T0).onScanSaved(event(trigger));

        assertThat(flags.saved).isEmpty();
    }

    @Test
    void repeatedOutOfRangeReportsTheDirectionWhenOnlyOneSideReachesTheThreshold() {
        scans.add(T0.minusSeconds(2 * 3600), "LOW", "5.8", "0.80", "CARD_CCM");
        scans.add(T0.minusSeconds(3600), "HIGH", "7.2", "0.80", "CARD_CCM");
        ScanHistoryQuery.RecentScan trigger = scans.add(T0, "SLIGHTLY_LOW", "6.1", "0.80", "CARD_CCM");

        service(repeatedRule(), T0).onScanSaved(event(trigger));

        assertThat(flags.saved).singleElement().satisfies(flag -> {
            assertThat(flag.getRuleCode()).isEqualTo(MonitoringRuleCode.REPEATED_OUT_OF_RANGE.name());
            assertThat(flag.getMessageParams()).containsEntry("direction", "axit").containsKeys("n", "windowHours");
        });
    }

    // Tên param là hợp đồng với mẫu healthFlags.message.rule.* ở frontend (common.json).
    @Test
    void lowQualityStreakEmitsTheParamsTheFrontendTemplateUses() {
        MonitoringRule r4 = MonitoringRuleFixtures.lowQualityStreak(2, 0.50, T0);
        scans.add(T0.minusSeconds(600), "NORMAL", "6.4", "0.40", "CARD_CCM");
        ScanHistoryQuery.RecentScan second = scans.add(T0, "NORMAL", "6.4", "0.40", "CARD_CCM");

        service(r4, T0).onScanSaved(event(second));

        assertThat(flags.saved).singleElement()
                .satisfies(flag -> assertThat(flag.getMessageParams()).containsEntry("n", 2));
    }

    @Test
    void baselineDeviationIgnoresALowConfidenceTriggerScan() {
        seedBaseline();
        ScanHistoryQuery.RecentScan trigger = scans.add(T0, "HIGH", "7.2", "0.40", "CARD_CCM");

        service(baselineRule(), T0).onScanSaved(event(trigger));

        assertThat(flags.saved).isEmpty();
    }

    @Test
    void baselineDeviationStillFiresForAReliableTriggerScan() {
        seedBaseline();
        ScanHistoryQuery.RecentScan trigger = scans.add(T0, "HIGH", "7.2", "0.80", "CARD_CCM");

        service(baselineRule(), T0).onScanSaved(event(trigger));

        assertThat(flags.saved).extracting(HealthFlag::getRuleCode)
                .containsExactly(MonitoringRuleCode.BASELINE_DEVIATION.name());
        assertThat(flags.saved.getFirst().getMessageParams()).containsKeys("ph", "baseline", "delta");
    }


    @Test
    void monotonicTrendIsNotReRaisedByAnUnrelatedFilteredScan() {
        seedRisingTrend();
        flags.saved.add(existingFlag(MonitoringRuleCode.MONOTONIC_TREND, T0.minus(Duration.ofDays(4))));
        ScanHistoryQuery.RecentScan lowConfidence = scans.add(T0, "HIGH", "7.0", "0.30", "CARD_CCM");

        service(trendRule(), T0).onScanSaved(event(lowConfidence));

        assertThat(flags.saved).hasSize(1);
    }

    @Test
    void monotonicTrendFiresWhenTheTriggerScanExtendsTheTrend() {
        seedRisingTrend();
        ScanHistoryQuery.RecentScan trigger = scans.add(T0, "HIGH", "6.9", "0.80", "CARD_CCM");

        service(trendRule(), T0).onScanSaved(event(trigger));

        assertThat(flags.saved).extracting(HealthFlag::getRuleCode)
                .containsExactly(MonitoringRuleCode.MONOTONIC_TREND.name());
        assertThat(flags.saved.getFirst().getMessageParams()).containsKeys("direction", "phSeries");
    }


    private void seedBaseline() {
        for (int day = 1; day <= 5; day++) {
            scans.add(T0.minus(Duration.ofDays(day)), "NORMAL", "6.4", "0.80", "CARD_CCM");
        }
    }

    private void seedRisingTrend() {
        scans.add(T0.minus(Duration.ofDays(6)), "NORMAL", "6.3", "0.80", "CARD_CCM");
        scans.add(T0.minus(Duration.ofDays(5)), "NORMAL", "6.5", "0.80", "CARD_CCM");
        scans.add(T0.minus(Duration.ofDays(4)), "SLIGHTLY_HIGH", "6.7", "0.80", "CARD_CCM");
    }

    private static MonitoringRule repeatedRule() {
        return MonitoringRuleFixtures.rule(MonitoringRuleCode.REPEATED_OUT_OF_RANGE.name(),
                Map.of("windowHours", 48, "minCount", 2, "minConfidence", 0.55),
                HealthFlagSeverity.ATTENTION, 24, "rule.REPEATED_OUT_OF_RANGE.message", T0);
    }

    private static MonitoringRule baselineRule() {
        return MonitoringRuleFixtures.rule(MonitoringRuleCode.BASELINE_DEVIATION.name(),
                Map.of("baselineDays", 14, "minDelta", 0.5, "minBaselineSamples", 5, "minConfidence", 0.55),
                HealthFlagSeverity.ATTENTION, 72, "rule.BASELINE_DEVIATION.message", T0);
    }

    private static MonitoringRule trendRule() {
        return MonitoringRuleFixtures.rule(MonitoringRuleCode.MONOTONIC_TREND.name(),
                Map.of("streak", 3, "maxSpanDays", 7, "minGapHours", 6, "minStepDelta", 0.15,
                        "midpoint", 6.45, "minConfidence", 0.55),
                HealthFlagSeverity.ATTENTION, 72, "rule.MONOTONIC_TREND.message", T0);
    }

    private static HealthFlag existingFlag(MonitoringRuleCode code, Instant at) {
        return new HealthFlag(UUID.randomUUID(), CAT_ID, code.name(), UUID.randomUUID(),
                HealthFlagSeverity.ATTENTION, at.minusSeconds(3600), at, "k", Map.of(), "x",
                code.name() + ":" + CAT_ID + ":old", at);
    }

    private RuleEvaluationService service(MonitoringRule enabled, Instant now) {
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        return new RuleEvaluationService(new SingleRule(enabled), flags, scans, new UuidV7(clock), clock);
    }

    private static ScanSavedEvent event(ScanHistoryQuery.RecentScan scan) {
        return new ScanSavedEvent(scan.scanId(), CAT_ID, UUID.randomUUID(), "ASSIGNED", scan.phValue(),
                scan.classification(), scan.confidence(), scan.nearBoundary(), scan.calibrationMethod(),
                scan.capturedAt(), false);
    }

    private static final class FakeScans implements ScanHistoryQuery {

        private final List<RecentScan> rows = new ArrayList<>();

        RecentScan add(Instant capturedAt, String classification, String ph, String confidence, String method) {
            RecentScan scan = new RecentScan(UUID.randomUUID(), capturedAt, classification,
                    new BigDecimal(ph), new BigDecimal(confidence), false, method);
            rows.add(scan);
            return scan;
        }

        @Override
        public List<RecentScan> recentValidScans(UUID catId, Instant since, int limit) {
            return rows.stream()
                    .filter(s -> !s.capturedAt().isBefore(since))
                    .sorted(Comparator.comparing(RecentScan::capturedAt).reversed())
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<ExportScanRow> scansForExport(UUID catId, Instant from, Instant to) {
            return List.of();
        }
    }

    private static final class FakeFlags implements HealthFlagRepository {

        private final List<HealthFlag> saved = new ArrayList<>();

        @Override
        public Optional<HealthFlag> saveIfNotDuplicate(HealthFlag flag) {
            if (saved.stream().anyMatch(f -> f.getDedupeKey().equals(flag.getDedupeKey()))) {
                return Optional.empty();
            }
            saved.add(flag);
            return Optional.of(flag);
        }

        @Override
        public List<HealthFlag> findByCatIdAndTriggeredAtBetween(UUID catId, Instant from, Instant to) {
            return saved.stream()
                    .filter(f -> f.getCatId().equals(catId))
                    .filter(f -> !f.getTriggeredAt().isBefore(from) && !f.getTriggeredAt().isAfter(to))
                    .toList();
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
    }

    private record SingleRule(MonitoringRule rule) implements MonitoringRuleRepository {

        @Override
        public Optional<MonitoringRule> findByCode(String code) {
            return rule.getCode().equals(code) ? Optional.of(rule) : Optional.empty();
        }

        @Override
        public List<MonitoringRule> findAllEnabled() {
            return List.of(rule);
        }

        @Override
        public List<MonitoringRule> findAllEnabledForDisplay() {
            return List.of(rule);
        }

        @Override
        public List<MonitoringRule> findAll() {
            return List.of(rule);
        }

        @Override
        public List<MonitoringRule> findAllForAdmin() {
            return List.of(rule);
        }

        @Override
        public MonitoringRule saveAndReload(MonitoringRule rule) {
            return rule;
        }
    }
}
