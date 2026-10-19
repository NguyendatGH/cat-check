package com.catcheck.ai.infrastructure.context;

import com.catcheck.ai.application.CatContextPort;
import com.catcheck.cat.api.CatContextQuery;
import com.catcheck.credit.api.CreditConsumption;
import com.catcheck.credit.api.EntitlementQuery;
import com.catcheck.insight.api.OpenHealthFlagQuery;
import com.catcheck.reminder.api.ActiveReminderQuery;
import com.catcheck.scan.api.ScanHistoryQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class CatContextAdapterTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000001");
    private static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-7000-8000-00000000000f");
    private static final UUID CAT_ID = UUID.fromString("00000000-0000-7000-8000-000000000002");
    private static final Instant NOW = Instant.parse("2026-10-09T09:00:00Z");

    private static final Pattern EMAIL = Pattern.compile(
            "(?i)(?<![A-Z0-9._%+-])[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}(?![A-Z0-9.-])");
    private static final Pattern VIETNAMESE_PHONE = Pattern.compile(
            "(?<!\\d)(?:\\+?84|0)(?:[\\s().-]*\\d){8,10}(?!\\d)");

    private FakeCatContextQuery catContextQuery;
    private FakeScanHistoryQuery scanHistoryQuery;
    private FakeOpenHealthFlagQuery openHealthFlagQuery;
    private FakeActiveReminderQuery activeReminderQuery;
    private FakeEntitlementQuery entitlementQuery;
    private FakeCreditConsumption creditConsumption;
    private CatContextAdapter adapter;

    @BeforeEach
    void setUp() {
        catContextQuery = new FakeCatContextQuery();
        scanHistoryQuery = new FakeScanHistoryQuery();
        openHealthFlagQuery = new FakeOpenHealthFlagQuery();
        activeReminderQuery = new FakeActiveReminderQuery();
        entitlementQuery = new FakeEntitlementQuery();
        creditConsumption = new FakeCreditConsumption();
        adapter = new CatContextAdapter(catContextQuery, scanHistoryQuery, openHealthFlagQuery,
                activeReminderQuery, entitlementQuery, creditConsumption, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("Mèo không thuộc userId ⇒ Optional.empty() và KHÔNG gọi bất kỳ cổng nào khác")
    void ownershipDeniedShortCircuitsBeforeAnyOtherPort() {
        catContextQuery.snapshot = null;

        Optional<CatContextPort.CatContext> result = adapter.loadContext(OTHER_USER_ID, CAT_ID);

        assertThat(result).isEmpty();
        assertThat(catContextQuery.lastOwnerId).isEqualTo(OTHER_USER_ID);
        assertThat(scanHistoryQuery.called).isFalse();
        assertThat(openHealthFlagQuery.called).isFalse();
        assertThat(activeReminderQuery.called).isFalse();
        assertThat(entitlementQuery.called).isFalse();
        assertThat(creditConsumption.called).isFalse();
    }

    @Test
    @DisplayName("Mèo chưa quét, chưa khảo sát, chưa có flag/reminder ⇒ context vẫn dựng được, không NPE")
    void emptyCatStillProducesContextWithoutNpe() {
        catContextQuery.snapshot = minimalSnapshot();

        CatContextPort.CatContext context = adapter.loadContext(USER_ID, CAT_ID).orElseThrow();

        assertThat(context.catId()).isEqualTo(CAT_ID);
        assertThat(context.latestScan()).isNull();
        assertThat(context.last30Days()).isNull();
        assertThat(context.openHealthFlags()).isEmpty();
        assertThat(context.recentClinicalSigns()).isEmpty();
        assertThat(context.survey()).isNull();
        assertThat(context.activeReminders()).isEmpty();
        assertThat(context.credit()).isNotNull();
    }

    @Test
    @DisplayName("Lần quét gần nhất nằm NGOÀI cửa sổ 30 ngày vẫn hiện ở latestScan, nhưng last30Days = null")
    void latestScanIsIndependentOfTrendWindow() {
        catContextQuery.snapshot = minimalSnapshot();
        scanHistoryQuery.scans.add(scan(NOW.minus(Duration.ofDays(45)), "IN_RANGE", new BigDecimal("6.40")));

        CatContextPort.CatContext context = adapter.loadContext(USER_ID, CAT_ID).orElseThrow();

        assertThat(context.latestScan()).isNotNull();
        assertThat(context.latestScan().phValue()).isEqualByComparingTo("6.40");
        assertThat(context.last30Days()).isNull();
    }

    @Test
    @DisplayName("Thống kê 30 ngày: trung vị + tỉ lệ trong dải tính đúng trên các lần quét hợp lệ")
    void trendSummaryComputesMedianAndInRangeRatio() {
        catContextQuery.snapshot = minimalSnapshot();
        scanHistoryQuery.scans.add(scan(NOW.minusSeconds(300), "IN_RANGE", new BigDecimal("6.50")));
        scanHistoryQuery.scans.add(scan(NOW.minusSeconds(200), "SLIGHTLY_HIGH", new BigDecimal("7.00")));
        scanHistoryQuery.scans.add(scan(NOW.minusSeconds(100), "IN_RANGE", new BigDecimal("6.00")));

        CatContextPort.TrendSummary trend = adapter.loadContext(USER_ID, CAT_ID).orElseThrow().last30Days();

        assertThat(trend).isNotNull();
        assertThat(trend.scanCount()).isEqualTo(3);
        assertThat(trend.medianPh()).isEqualByComparingTo("6.50");
        assertThat(trend.inRangeRatio()).isEqualByComparingTo("0.67");
    }

    @Test
    @DisplayName("Snapshot đã lắp đủ dữ liệu: toString() của CatContext không khớp regex email/SĐT")
    void catContextToStringNeverLooksLikePii() {
        catContextQuery.snapshot = new CatContextQuery.CatProfileSnapshot(
                CAT_ID, "Mun Mun", 24, "BRITISH_SHORTHAIR", null, "FEMALE", true,
                new BigDecimal("4.20"), NOW,
                new CatContextQuery.SurveyAnswers("v1", "CATCHECK_SMART", "NONE", "DRY", "1_2_PER_DAY",
                        List.of("STRAINING"), NOW),
                List.of(new CatContextQuery.ClinicalSignEntry(List.of("STRAINING"), NOW, false)));
        scanHistoryQuery.scans.add(scan(NOW.minusSeconds(60), "IN_RANGE", new BigDecimal("6.50")));
        openHealthFlagQuery.flags.add(new OpenHealthFlagQuery.OpenFlag("REPEATED_OUT_OF_RANGE", "ATTENTION", NOW));
        activeReminderQuery.reminders.add(new ActiveReminderQuery.ActiveReminder("SCAN_ROUTINE", NOW.plusSeconds(86_400)));
        entitlementQuery.writeAccess = true;
        entitlementQuery.maxCatProfiles = 4;
        creditConsumption.hasCredit = true;

        CatContextPort.CatContext context = adapter.loadContext(USER_ID, CAT_ID).orElseThrow();
        String rendered = context.toString();

        assertThat(EMAIL.matcher(rendered).find()).isFalse();
        assertThat(VIETNAMESE_PHONE.matcher(rendered).find()).isFalse();
    }

    private static CatContextQuery.CatProfileSnapshot minimalSnapshot() {
        return new CatContextQuery.CatProfileSnapshot(
                CAT_ID, "Mun", 12, null, null, "UNKNOWN", null, null, null, null, List.of());
    }

    private static ScanHistoryQuery.RecentScan scan(Instant capturedAt, String classification, BigDecimal phValue) {
        return new ScanHistoryQuery.RecentScan(
                UUID.randomUUID(), capturedAt, classification, phValue, new BigDecimal("0.90"), false, "CARD_CCM");
    }

    private static final class FakeCatContextQuery implements CatContextQuery {
        private CatProfileSnapshot snapshot;
        private UUID lastOwnerId;

        @Override
        public Optional<CatProfileSnapshot> findSnapshot(UUID ownerId, UUID catId) {
            this.lastOwnerId = ownerId;
            return Optional.ofNullable(snapshot);
        }
    }

    private static final class FakeScanHistoryQuery implements ScanHistoryQuery {
        private final List<RecentScan> scans = new ArrayList<>();
        private boolean called;

        @Override
        public List<RecentScan> recentValidScans(UUID catId, Instant since, int limit) {
            called = true;
            return scans.stream()
                    .filter(s -> !s.capturedAt().isBefore(since))
                    .sorted(Comparator.comparing(RecentScan::capturedAt).reversed())
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<ExportScanRow> scansForExport(UUID catId, Instant from, Instant to) {
            throw new UnsupportedOperationException("not used by CatContextAdapter");
        }
    }

    private static final class FakeOpenHealthFlagQuery implements OpenHealthFlagQuery {
        private final List<OpenFlag> flags = new ArrayList<>();
        private boolean called;

        @Override
        public List<OpenFlag> openFlagsFor(UUID userId, UUID catId, int limit) {
            called = true;
            return flags;
        }
    }

    private static final class FakeActiveReminderQuery implements ActiveReminderQuery {
        private final List<ActiveReminder> reminders = new ArrayList<>();
        private boolean called;

        @Override
        public List<ActiveReminder> activeRemindersFor(UUID userId, UUID catId) {
            called = true;
            return reminders;
        }
    }

    private static final class FakeEntitlementQuery implements EntitlementQuery {
        private boolean called;
        private boolean writeAccess;
        private Integer maxCatProfiles;

        @Override
        public com.catcheck.credit.domain.Entitlement snapshot(UUID userId) {
            throw new UnsupportedOperationException("CatContextAdapter cố ý không gọi snapshot() "
                    + "(Entitlement nằm ngoài named interface credit::api)");
        }

        @Override
        public Integer maxCatProfiles(UUID userId) {
            called = true;
            return maxCatProfiles;
        }

        @Override
        public boolean isFeatureEnabled(UUID userId, Feature feature) {
            called = true;
            return false;
        }

        @Override
        public boolean hasWriteAccess(UUID userId) {
            called = true;
            return writeAccess;
        }

        @Override
        public CatProfileAllowance checkCatProfileAllowance(UUID userId, int currentCount) {
            throw new UnsupportedOperationException("not used by CatContextAdapter");
        }
    }

    private static final class FakeCreditConsumption implements CreditConsumption {
        private boolean called;
        private boolean hasCredit;

        @Override
        public CreditCharge consume(UUID userId, CreditConsumeCommand command) {
            throw new UnsupportedOperationException("not used by CatContextAdapter");
        }

        @Override
        public CreditRefund refund(UUID userId, CreditRefundCommand command) {
            throw new UnsupportedOperationException("not used by CatContextAdapter");
        }

        @Override
        public boolean hasAvailableCredit(UUID userId) {
            called = true;
            return hasCredit;
        }

        @Override
        public boolean hasTrialScanRemaining(UUID userId) {
            called = true;
            return false;
        }

        @Override
        public boolean consumeTrialScan(UUID userId) {
            throw new UnsupportedOperationException("not used by CatContextAdapter");
        }
    }
}
