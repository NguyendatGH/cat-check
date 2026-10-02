package com.catcheck.cat.application;

import com.catcheck.cat.api.CatErrorCode;
import com.catcheck.cat.application.spi.FeatureEntitlementPort;
import com.catcheck.cat.application.spi.ScanInsightPort;
import com.catcheck.cat.domain.port.CatRepository;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.NotFoundException;
import com.catcheck.shared.error.PermissionDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * D12 {@code GET /cats/{catId}/summary} + D13 {@code GET /cats/{catId}/trends} (p8 §8.4.4).
 *
 * <p>Thay cho hai stub 501 cu o {@code CatController}. Doc du lieu quet qua
 * {@link ScanInsightPort} — cong SPI rieng cua cat, KHONG phu thuoc module {@code scan}.</p>
 */
@Service
public class CatInsightService {

    /** Cua so co dinh cho cac chi so {@code *30d} cua D12 (p8 §8.9 dong 1185). */
    private static final Duration SUMMARY_WINDOW = Duration.ofDays(30);

    /**
     * Nguong "do tin cay thap" dung cho {@code stats.lowConfidenceCount} — lay dung con so
     * module {@code scan} dang dung ({@code JdbcScanQueryRepository}: {@code confidence < 0.55})
     * de hai noi khong bao cao lech nhau. Nen chuyen vao {@code app_setting} de mot nguon duy nhat.
     */
    private static final BigDecimal LOW_CONFIDENCE = new BigDecimal("0.55");

    /**
     * So diem ket luan duoc toi thieu de coi la "du du lieu ve xu huong".
     *
     * <p>p6 §6.x mo ta thong diep xu huong dua tren <b>3 lan quet gan nhat</b>
     * ("3 lan quet gan nhat cua {catName} ({ph1} → {ph2} → {ph3})"), nen lay 3 lam nguong.
     * Spec KHONG dinh nghia tuong minh con so cho {@code hasEnoughDataForTrend} — can owner
     * xac nhan.
     */
    private static final int MIN_POINTS_FOR_TREND = 3;

    /** Khoa tinh nang trong {@code user_entitlement.features} (p5 §5.3, {@code PlanFeature.TREND}). */
    private static final String FEATURE_TREND = "trend";

    private static final int MAX_CUSTOM_RANGE_DAYS = 365;

    private final CatRepository catRepository;
    private final ScanInsightPort scanInsightPort;
    private final FeatureEntitlementPort featureEntitlementPort;
    private final Clock clock;

    public CatInsightService(
            CatRepository catRepository,
            ScanInsightPort scanInsightPort,
            FeatureEntitlementPort featureEntitlementPort,
            Clock clock) {
        this.catRepository = catRepository;
        this.scanInsightPort = scanInsightPort;
        this.featureEntitlementPort = featureEntitlementPort;
        this.clock = clock;
    }

    /** D12 — {@code U:own}. */
    @Transactional(readOnly = true)
    public CatSummaryView summary(UUID ownerId, UUID catId) {
        requireOwnedCat(ownerId, catId);
        Instant now = clock.instant();
        Instant from = now.minus(SUMMARY_WINDOW);

        ScanInsightPort.WindowStats stats = scanInsightPort.windowStats(catId, from, now);
        BigDecimal inRangeRatio = stats.conclusiveCount() == 0
                ? null
                : BigDecimal.valueOf(stats.inRangeCount())
                        .divide(BigDecimal.valueOf(stats.conclusiveCount()), 3, RoundingMode.HALF_UP);

        return new CatSummaryView(
                catId,
                scanInsightPort.lastScanOf(catId).orElse(null),
                stats.scanCount(),
                inRangeRatio,
                scanInsightPort.unacknowledgedFlagCount(catId),
                // Nhac lich quet la M5 (reminder) — chua co bang/API nao, ORCHESTRATOR §2 loai
                // khoi MVP. Tra null dung theo hop dong thay vi bia gia tri.
                null,
                stats.conclusiveCount() >= MIN_POINTS_FOR_TREND);
    }

    /** D13 — {@code U:own} + {@code E:trend}. */
    @Transactional(readOnly = true)
    public CatTrendsView trends(UUID ownerId, UUID catId, String range, Instant from, Instant to) {
        requireOwnedCat(ownerId, catId);
        if (!featureEntitlementPort.isFeatureEnabled(ownerId, FEATURE_TREND)) {
            throw new PermissionDeniedException(CatErrorCode.FEATURE_NOT_IN_PLAN, FEATURE_TREND);
        }

        Instant now = clock.instant();
        TrendRange resolved = resolveRange(range, from, to, now);
        List<ScanInsightPort.TrendPoint> points =
                scanInsightPort.trendPoints(catId, resolved.from(), resolved.to());

        return new CatTrendsView(
                resolved.label(),
                resolved.from(),
                resolved.to(),
                points,
                computeStats(points),
                scanInsightPort.activeBands(),
                scanInsightPort.chartVersionsIn(catId, resolved.from(), resolved.to()));
    }

    /**
     * {@code ?range=7D|30D|90D|CUSTOM}. Voi {@code CUSTOM} phai co ca {@code from} lan {@code to}.
     * Gia tri sai / khoang dao nguoc / qua dai ⇒ {@code 400 VALIDATION_FAILED}.
     *
     * <p>p8 liet ke {@code EXPORT_RANGE_INVALID} cho endpoint nay, nhung ma do thuoc so huu cua
     * module {@code export}; dung lai o {@code cat} se lam lech quyen so huu ma loi, nen dung
     * {@code VALIDATION_FAILED} kem ten tham so — can owner xac nhan.</p>
     */
    private TrendRange resolveRange(String range, Instant from, Instant to, Instant now) {
        String normalized = range == null || range.isBlank() ? "30D" : range.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "7D" -> new TrendRange("7D", now.minus(Duration.ofDays(7)), now);
            case "30D" -> new TrendRange("30D", now.minus(Duration.ofDays(30)), now);
            case "90D" -> new TrendRange("90D", now.minus(Duration.ofDays(90)), now);
            case "CUSTOM" -> {
                if (from == null || to == null || !from.isBefore(to)
                        || Duration.between(from, to).toDays() > MAX_CUSTOM_RANGE_DAYS) {
                    throw new BusinessRuleException(CatErrorCode.VALIDATION_FAILED, "range");
                }
                yield new TrendRange("CUSTOM", from, to);
            }
            default -> throw new BusinessRuleException(CatErrorCode.VALIDATION_FAILED, "range");
        };
    }

    /** Median tinh trong Java tren tap diem da lay — tap nay von nho (toi da vai tram diem). */
    private TrendStats computeStats(List<ScanInsightPort.TrendPoint> points) {
        List<BigDecimal> values = points.stream()
                .map(ScanInsightPort.TrendPoint::phValue)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.naturalOrder())
                .toList();

        long inRangeCount = points.stream()
                .filter(p -> "IN_RANGE".equals(p.classification()))
                .count();
        long lowConfidenceCount = points.stream()
                .filter(p -> p.confidence() != null && p.confidence().compareTo(LOW_CONFIDENCE) < 0)
                .count();

        if (values.isEmpty()) {
            return new TrendStats(null, null, null, points.size(), inRangeCount, lowConfidenceCount);
        }
        int size = values.size();
        BigDecimal median = size % 2 == 1
                ? values.get(size / 2)
                : values.get(size / 2 - 1).add(values.get(size / 2))
                        .divide(BigDecimal.valueOf(2), 1, RoundingMode.HALF_UP);

        return new TrendStats(
                median, values.getFirst(), values.getLast(),
                points.size(), inRangeCount, lowConfidenceCount);
    }

    private void requireOwnedCat(UUID ownerId, UUID catId) {
        catRepository.findByIdAndOwnerId(catId, ownerId)
                .orElseThrow(() -> new NotFoundException(CatErrorCode.CAT_NOT_FOUND));
    }

    private record TrendRange(String label, Instant from, Instant to) {
    }

    /** @param inRangeRatio30d {@code null} khi chua co lan quet nao ket luan duoc (tranh chia 0) */
    public record CatSummaryView(
            UUID catId,
            ScanInsightPort.LastScan lastScan,
            long scanCount30d,
            BigDecimal inRangeRatio30d,
            long unacknowledgedFlagCount,
            Instant nextReminderAt,
            boolean hasEnoughDataForTrend) {
    }

    public record CatTrendsView(
            String range,
            Instant from,
            Instant to,
            List<ScanInsightPort.TrendPoint> points,
            TrendStats stats,
            List<ScanInsightPort.PhBandView> bands,
            List<Integer> chartVersions) {
    }

    /** @param median {@code null} khi khong co diem nao co {@code phValue} */
    public record TrendStats(
            BigDecimal median,
            BigDecimal min,
            BigDecimal max,
            int count,
            long inRangeCount,
            long lowConfidenceCount) {
    }
}
