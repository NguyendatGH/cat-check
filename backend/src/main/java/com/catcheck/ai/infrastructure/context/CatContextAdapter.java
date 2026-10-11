package com.catcheck.ai.infrastructure.context;

import com.catcheck.ai.application.CatContextPort;
import com.catcheck.cat.api.CatContextQuery;
import com.catcheck.credit.api.CreditConsumption;
import com.catcheck.credit.api.EntitlementQuery;
import com.catcheck.insight.api.OpenHealthFlagQuery;
import com.catcheck.reminder.api.ActiveReminderQuery;
import com.catcheck.scan.api.ScanHistoryQuery;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Hiện thực {@link CatContextPort} — MỘT adapter duy nhất gọi thẳng 5 named interface
 * (cat/scan/insight/reminder/credit), theo phương án đơn giản hơn trong hai phương án gợi ý ở
 * {@code docs/cat-rag-credit-pipeline.md} mục A4-(1): mỗi lệnh gọi ở đây map gần như 1-1 sang
 * một trường của {@link CatContextPort.CatContext}, nên tách thêm SPI con cho từng module không
 * mua được gì ở quy mô này (ponytail: tối thiểu, không gián tiếp thừa).
 *
 * <p><b>Thứ tự gọi là bất biến bảo mật:</b> {@link #loadContext} luôn gọi
 * {@code catContextQuery.findSnapshot} TRƯỚC — nếu rỗng, trả rỗng ngay và KHÔNG gọi bất kỳ cổng
 * nào khác. Bốn cổng còn lại chỉ nhận đúng {@code catId} đã được xác nhận thuộc {@code userId}.</p>
 */
@Component
public class CatContextAdapter implements CatContextPort {

    /** Cửa sổ "tóm tắt gần đây" của A4-(1). */
    private static final Duration TREND_WINDOW = Duration.ofDays(30);

    /** Đủ dư cho mọi mèo quét hằng ngày suốt 30 ngày mà không mất dữ liệu đầu cửa sổ. */
    private static final int TREND_FETCH_LIMIT = 90;

    private static final int OPEN_FLAG_LIMIT = 20;

    /** Dưới ngưỡng này thì chưa đủ để gọi là "xu hướng" — {@link CatContextPort.TrendSummary} trả {@code null}. */
    private static final int MIN_POINTS_FOR_TREND = 2;

    private static final String IN_RANGE_CLASSIFICATION = "IN_RANGE";

    private final CatContextQuery catContextQuery;
    private final ScanHistoryQuery scanHistoryQuery;
    private final OpenHealthFlagQuery openHealthFlagQuery;
    private final ActiveReminderQuery activeReminderQuery;
    private final EntitlementQuery entitlementQuery;
    private final CreditConsumption creditConsumption;
    private final Clock clock;

    public CatContextAdapter(
            CatContextQuery catContextQuery,
            ScanHistoryQuery scanHistoryQuery,
            OpenHealthFlagQuery openHealthFlagQuery,
            ActiveReminderQuery activeReminderQuery,
            EntitlementQuery entitlementQuery,
            CreditConsumption creditConsumption,
            Clock clock) {
        this.catContextQuery = catContextQuery;
        this.scanHistoryQuery = scanHistoryQuery;
        this.openHealthFlagQuery = openHealthFlagQuery;
        this.activeReminderQuery = activeReminderQuery;
        this.entitlementQuery = entitlementQuery;
        this.creditConsumption = creditConsumption;
        this.clock = clock;
    }

    @Override
    public Optional<CatContext> loadContext(UUID userId, UUID catId) {
        Optional<CatContextQuery.CatProfileSnapshot> profile = catContextQuery.findSnapshot(userId, catId);
        if (profile.isEmpty()) {
            return Optional.empty();
        }
        CatContextQuery.CatProfileSnapshot snapshot = profile.get();
        Instant now = clock.instant();

        // Lần quét gần nhất KHÔNG giới hạn trong cửa sổ 30 ngày (mèo lâu không quét vẫn nên thấy
        // lần quét cuối cùng), còn thống kê xu hướng thì giới hạn đúng cửa sổ.
        List<ScanHistoryQuery.RecentScan> latestWindow =
                scanHistoryQuery.recentValidScans(catId, Instant.EPOCH, 1);
        List<ScanHistoryQuery.RecentScan> trendWindow =
                scanHistoryQuery.recentValidScans(catId, now.minus(TREND_WINDOW), TREND_FETCH_LIMIT);

        return Optional.of(new CatContext(
                snapshot.catId(),
                snapshot.name(),
                snapshot.ageMonths(),
                snapshot.breedCode(),
                snapshot.breedOther(),
                snapshot.sex(),
                snapshot.neutered(),
                snapshot.weightKg(),
                snapshot.weightUpdatedAt(),
                latestScanOf(latestWindow),
                trendOf(trendWindow),
                openFlagsOf(userId, catId),
                clinicalSignsOf(snapshot),
                surveyOf(snapshot),
                remindersOf(userId, catId),
                creditOf(userId)));
    }

    private LatestScanSummary latestScanOf(List<ScanHistoryQuery.RecentScan> latestWindow) {
        if (latestWindow.isEmpty()) {
            return null;
        }
        ScanHistoryQuery.RecentScan latest = latestWindow.getFirst();
        return new LatestScanSummary(
                latest.capturedAt(), latest.phValue(), latest.classification(),
                latest.confidence(), latest.calibrationMethod(), latest.nearBoundary());
    }

    private TrendSummary trendOf(List<ScanHistoryQuery.RecentScan> trendWindow) {
        if (trendWindow.size() < MIN_POINTS_FOR_TREND) {
            return null;
        }
        List<BigDecimal> phValues = trendWindow.stream()
                .map(ScanHistoryQuery.RecentScan::phValue)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.naturalOrder())
                .toList();
        long inRangeCount = trendWindow.stream()
                .filter(scan -> IN_RANGE_CLASSIFICATION.equals(scan.classification()))
                .count();
        BigDecimal inRangeRatio = BigDecimal.valueOf(inRangeCount)
                .divide(BigDecimal.valueOf(trendWindow.size()), 2, RoundingMode.HALF_UP);
        return new TrendSummary(trendWindow.size(), median(phValues), inRangeRatio);
    }

    private BigDecimal median(List<BigDecimal> sortedValues) {
        if (sortedValues.isEmpty()) {
            return null;
        }
        int size = sortedValues.size();
        if (size % 2 == 1) {
            return sortedValues.get(size / 2);
        }
        return sortedValues.get(size / 2 - 1).add(sortedValues.get(size / 2))
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    private List<OpenHealthFlag> openFlagsOf(UUID userId, UUID catId) {
        return openHealthFlagQuery.openFlagsFor(userId, catId, OPEN_FLAG_LIMIT).stream()
                .map(flag -> new OpenHealthFlag(flag.ruleCode(), flag.severity(), flag.triggeredAt()))
                .toList();
    }

    private List<RecentClinicalSign> clinicalSignsOf(CatContextQuery.CatProfileSnapshot snapshot) {
        return snapshot.recentClinicalSigns().stream()
                .map(entry -> new RecentClinicalSign(entry.signs(), entry.reportedAt(), entry.acknowledged()))
                .toList();
    }

    private SurveySummary surveyOf(CatContextQuery.CatProfileSnapshot snapshot) {
        CatContextQuery.SurveyAnswers survey = snapshot.survey();
        if (survey == null) {
            return null;
        }
        return new SurveySummary(survey.litterType(), survey.urinaryHistory(), survey.dietType(),
                survey.urinationFrequency(), survey.symptoms());
    }

    private List<ActiveReminder> remindersOf(UUID userId, UUID catId) {
        return activeReminderQuery.activeRemindersFor(userId, catId).stream()
                .map(reminder -> new ActiveReminder(reminder.type(), reminder.nextRunAt()))
                .toList();
    }

    private CreditSnapshot creditOf(UUID userId) {
        return new CreditSnapshot(
                creditConsumption.hasAvailableCredit(userId),
                creditConsumption.hasTrialScanRemaining(userId),
                entitlementQuery.hasWriteAccess(userId),
                entitlementQuery.maxCatProfiles(userId));
    }
}
