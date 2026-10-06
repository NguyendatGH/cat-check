package com.catcheck.colorchart.application;

import com.catcheck.audit.api.AuditEvent;
import com.catcheck.colorchart.api.ColorChartErrorCode;
import com.catcheck.colorchart.domain.ChartSource;
import com.catcheck.colorchart.domain.ChartStatus;
import com.catcheck.colorchart.domain.ColorChart;
import com.catcheck.colorchart.domain.ColorChartPoint;
import com.catcheck.colorchart.domain.PageResult;
import com.catcheck.colorchart.domain.ProductLine;
import com.catcheck.colorchart.domain.port.ColorChartPointRepository;
import com.catcheck.colorchart.domain.port.ColorChartRepository;
import com.catcheck.scan.api.ChartBackfill;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.CatCheckException;
import com.catcheck.shared.error.NotFoundException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L33–L35 phía nghiệp vụ quản trị — ba nhánh lỗi mà p8 §8.4.12 nêu đích danh
 * ({@code 409 COLOR_CHART_IN_USE}, {@code 422 COLOR_CHART_INCOMPLETE}) cộng cửa sổ thời gian.
 */
class ChartBackfillAdminServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-06T10:00:00Z");
    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    private final FakeChartRepository charts = new FakeChartRepository();
    private final FakePointRepository points = new FakePointRepository();
    private final RecordingBackfill backfill = new RecordingBackfill();
    private final List<AuditEvent> audits = new ArrayList<>();
    private final ChartBackfillAdminService service =
            new ChartBackfillAdminService(charts, points, backfill, audits::add);

    private static final AdminAction ACTION = new AdminAction(
            ACTOR, "ADMIN_CATALOG", "Hieu chuan bang mau lo 2026A", "req-1", "127.0.0.1", "curl");

    @Test
    void previewOnAnArchivedChartIsRejectedWithChartInUse() {
        UUID chartId = charts.add(ChartStatus.ARCHIVED);
        points.add(chartId, 2);

        CatCheckException ex = assertThrows(BusinessRuleException.class,
                () -> service.startPreview(chartId, "P90D", ACTION));

        assertEquals(ColorChartErrorCode.COLOR_CHART_IN_USE, ex.errorCode());
        assertEquals(409, ex.errorCode().status().value());
        assertTrue(backfill.previews.isEmpty(), "Khong duoc dua job nao vao hang doi");
    }

    @Test
    void previewOnAChartWithFewerThanTwoPointsIsRejectedWithIncomplete() {
        UUID chartId = charts.add(ChartStatus.DRAFT);
        points.add(chartId, 1);

        CatCheckException ex = assertThrows(BusinessRuleException.class,
                () -> service.startPreview(chartId, "P90D", ACTION));

        assertEquals(ColorChartErrorCode.COLOR_CHART_INCOMPLETE, ex.errorCode());
        assertEquals(422, ex.errorCode().status().value());
    }

    @Test
    void previewOnAnUnknownChartIsNotFound() {
        CatCheckException ex = assertThrows(NotFoundException.class,
                () -> service.startPreview(UUID.randomUUID(), "P90D", ACTION));

        assertEquals(ColorChartErrorCode.COLOR_CHART_NOT_FOUND, ex.errorCode());
    }

    @Test
    void previewOnADraftChartIsAllowedAndAudited() {
        UUID chartId = charts.add(ChartStatus.DRAFT);
        points.add(chartId, 7);

        service.startPreview(chartId, "P6M", ACTION);

        assertEquals(List.of(chartId), backfill.previews, "Bang DRAFT phai backfill duoc — do la muc dich cua L33");
        assertEquals(Period.ofMonths(6), backfill.lastWindow);
        assertEquals(1, audits.size());
        AuditEvent event = audits.getFirst();
        assertEquals("ADMIN_CHART_BACKFILL_PREVIEW", event.action());
        assertEquals("Hieu chuan bang mau lo 2026A", event.metadata().get("reason"));
        assertEquals(chartId.toString(), event.metadata().get("chartId"));
    }

    @Test
    void applyWithoutAPreviewIsRejectedWithIncomplete() {
        UUID chartId = charts.add(ChartStatus.ACTIVE);
        points.add(chartId, 7);

        CatCheckException ex = assertThrows(BusinessRuleException.class,
                () -> service.startApply(chartId, ACTION));

        assertEquals(ColorChartErrorCode.COLOR_CHART_INCOMPLETE, ex.errorCode());
        assertTrue(backfill.applies.isEmpty(),
                "Ap dung mot preview chua chay se 'thanh cong' ma khong doi dong nao — phai chan");
    }

    @Test
    void applyAfterAPreviewRunsAndRecordsTheImpactInAudit() {
        UUID chartId = charts.add(ChartStatus.ACTIVE);
        points.add(chartId, 7);
        backfill.impact = new ChartBackfill.BackfillImpact(40L, 3L, new BigDecimal("0.40"),
                UUID.randomUUID(), List.of());

        service.startApply(chartId, ACTION);

        assertEquals(List.of(chartId), backfill.applies);
        AuditEvent event = audits.getFirst();
        assertEquals("ADMIN_CHART_BACKFILL_APPLY", event.action());
        assertEquals(40L, event.metadata().get("evaluated"));
        assertEquals(3L, event.metadata().get("flipped"));
    }

    @Test
    void windowDefaultsToNinetyDaysWhenAbsent() {
        assertEquals(Period.ofDays(90), ChartBackfillAdminService.parseWindow(null));
        assertEquals(Period.ofDays(90), ChartBackfillAdminService.parseWindow("  "));
    }

    @Test
    void malformedZeroOrOversizedWindowsAreRejected() {
        assertEquals(ColorChartErrorCode.VALIDATION_FAILED,
                assertThrows(BusinessRuleException.class,
                        () -> ChartBackfillAdminService.parseWindow("90 days")).errorCode());
        assertEquals(ColorChartErrorCode.VALIDATION_FAILED,
                assertThrows(BusinessRuleException.class,
                        () -> ChartBackfillAdminService.parseWindow("P0D")).errorCode());
        assertEquals(ColorChartErrorCode.VALIDATION_FAILED,
                assertThrows(BusinessRuleException.class,
                        () -> ChartBackfillAdminService.parseWindow("P100Y")).errorCode(),
                "P100Y quet toan bo scan_analysis trong mot transaction — phai co tran");
    }

    // ------------------------------------------------------------------ fixtures

    /** {@link ChartBackfill} chỉ ghi lại lời gọi — phép tính đã có test riêng ở module scan. */
    private static final class RecordingBackfill implements ChartBackfill {

        private final List<UUID> previews = new ArrayList<>();
        private final List<UUID> applies = new ArrayList<>();
        private Period lastWindow;
        private BackfillImpact impact = new BackfillImpact(0L, 0L, null, null, List.of());

        @Override
        public void startPreview(UUID chartId, Period window) {
            previews.add(chartId);
            lastWindow = window;
        }

        @Override
        public BackfillImpact impact(UUID chartId) {
            return impact;
        }

        @Override
        public void startApply(UUID chartId) {
            applies.add(chartId);
        }
    }

    private static final class FakeChartRepository implements ColorChartRepository {

        private final Map<UUID, ColorChart> store = new java.util.LinkedHashMap<>();

        private UUID add(ChartStatus status) {
            UUID id = UUID.randomUUID();
            store.put(id, new ColorChart(id, "CHART-TEST", 1, "Bang mau test", ProductLine.STANDARD,
                    "BATCH-2026A", null, status, true, "D65", "2", "CIEDE2000",
                    Map.of("kL", 2), ChartSource.MANUAL_HEX, null, null, null, null,
                    ACTOR, NOW, NOW));
            return id;
        }

        @Override
        public ColorChart save(ColorChart chart) {
            store.put(chart.getId(), chart);
            return chart;
        }

        @Override
        public Optional<ColorChart> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<ColorChart> findByCodeAndVersion(String code, int version) {
            return Optional.empty();
        }

        @Override
        public Optional<ColorChart> findActiveByProductLineAndBatch(ProductLine line, String batch) {
            return store.values().stream().filter(c -> c.getStatus() == ChartStatus.ACTIVE).findFirst();
        }

        @Override
        public boolean existsActiveByProductLineAndBatch(ProductLine line, String batch) {
            return findActiveByProductLineAndBatch(line, batch).isPresent();
        }

        private List<ColorChart> byStatus(ChartStatus status) {
            return store.values().stream().filter(c -> c.getStatus() == status).toList();
        }

        @Override
        public PageResult<ColorChart> findAll(int page, int size) {
            return new PageResult<>(List.copyOf(store.values()), page, size, store.size(), 1, false);
        }

        @Override
        public PageResult<ColorChart> findAllByStatus(ChartStatus status, int page, int size) {
            List<ColorChart> items = byStatus(status);
            return new PageResult<>(items, page, size, items.size(), 1, false);
        }
    }

    private static final class FakePointRepository implements ColorChartPointRepository {

        private final Map<UUID, List<ColorChartPoint>> store = new java.util.LinkedHashMap<>();

        private void add(UUID chartId, int count) {
            List<ColorChartPoint> list = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                list.add(new ColorChartPoint(UUID.randomUUID(), chartId,
                        BigDecimal.valueOf(5.5 + i * 0.5), i,
                        BigDecimal.valueOf(70 - i), BigDecimal.valueOf(20 - i), BigDecimal.valueOf(30 - i),
                        new BigDecimal("4.00"), "#AABBCC", "#AABBCC", "vi", "en", 0, null, NOW, NOW));
            }
            store.put(chartId, list);
        }

        @Override
        public List<ColorChartPoint> findByChartIdOrderBySortOrder(UUID chartId) {
            return store.getOrDefault(chartId, List.of());
        }

        @Override
        public int deleteByChartId(UUID chartId) {
            List<ColorChartPoint> removed = store.remove(chartId);
            return removed == null ? 0 : removed.size();
        }

        @Override
        public ColorChartPoint save(ColorChartPoint point) {
            return point;
        }

        @Override
        public void saveAll(Iterable<ColorChartPoint> newPoints) {
            // Backfill chi doc diem, khong ghi — fake nay khong can luu.
        }
    }
}
