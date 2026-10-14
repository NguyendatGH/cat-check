package com.catcheck.colorchart.application;

import com.catcheck.colorchart.api.ColorChartErrorCode;
import com.catcheck.colorchart.domain.ChartSource;
import com.catcheck.colorchart.domain.ChartStatus;
import com.catcheck.colorchart.domain.ColorChart;
import com.catcheck.colorchart.domain.ColorChartPoint;
import com.catcheck.colorchart.domain.PageResult;
import com.catcheck.colorchart.domain.ProductLine;
import com.catcheck.colorchart.domain.port.ColorChartPointRepository;
import com.catcheck.colorchart.domain.port.ColorChartRepository;
import com.catcheck.shared.error.ConflictException;
import com.catcheck.shared.id.UuidV7;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColorChartAdminServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-09T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final FakeChartRepository charts = new FakeChartRepository();
    private final FakePointRepository points = new FakePointRepository();
    private final ChartPublishValidator validator = new ChartPublishValidator();
    private final ColorChartAdminService service =
            new ColorChartAdminService(charts, points, validator, new UuidV7(CLOCK), CLOCK);

    @Test
    void publishPreservesPlaceholderUntilCalibrationEvidenceIsAvailable() {
        UUID chartId = charts.add(ChartStatus.DRAFT, ProductLine.STANDARD, "LOT-2026A", true);
        points.seedValidPoints(chartId);

        ColorChart published = service.publish(chartId);

        assertEquals(ChartStatus.ACTIVE, published.getStatus());
        assertTrue(published.isPlaceholder(),
                "Publish validates point structure only; it must not relabel seeded data as calibrated");
    }

    @Test
    void publishMapsConcurrentUniqueViolationToConflictInsteadOfFiveHundred() {
        UUID chartId = charts.add(ChartStatus.DRAFT, ProductLine.STANDARD, "LOT-2026A", true);
        points.seedValidPoints(chartId);
        charts.throwOnNextActiveSave();

        ConflictException ex = assertThrows(ConflictException.class, () -> service.publish(chartId));

        assertEquals(ColorChartErrorCode.COLOR_CHART_IN_USE, ex.errorCode());
        assertEquals(409, ex.errorCode().status().value());
    }

    @Test
    void publishReplacesTheActiveChartOfTheSameLineAndArchivesIt() {
        UUID oldId = charts.add(ChartStatus.ACTIVE, ProductLine.STANDARD, "LOT-2026A", true);
        UUID draftId = charts.add(ChartStatus.DRAFT, ProductLine.STANDARD, "LOT-2026A", true);
        points.seedValidPoints(draftId);

        ColorChart published = service.publish(draftId);

        assertEquals(ChartStatus.ACTIVE, published.getStatus());
        ColorChart old = charts.findById(oldId).orElseThrow();
        assertEquals(ChartStatus.ARCHIVED, old.getStatus());
        assertEquals(NOW, old.getEffectiveTo());
    }

    @Test
    void createDraftUsesTheNextFreeVersionAfterTwoExistingVersions() {
        charts.add("CHART-TEST", 1, ChartStatus.ARCHIVED);
        charts.add("CHART-TEST", 2, ChartStatus.ACTIVE);

        ColorChart draft = service.createDraft("CHART-TEST", "Ban 3", ProductLine.STANDARD,
                "LOT-2026A", null, UUID.randomUUID());

        assertEquals(3, draft.getVersion());
    }


    private static final class FakeChartRepository implements ColorChartRepository {

        private final Map<UUID, ColorChart> store = new LinkedHashMap<>();
        private boolean throwOnNextActiveSave;

        void throwOnNextActiveSave() {
            this.throwOnNextActiveSave = true;
        }

        UUID add(ChartStatus status, ProductLine productLine, String productionBatch, boolean placeholder) {
            return add("CHART-TEST", 1, status, productLine, productionBatch, placeholder);
        }

        UUID add(String code, int version, ChartStatus status) {
            return add(code, version, status, ProductLine.STANDARD, "LOT-2026A", true);
        }

        private UUID add(String code, int version, ChartStatus status, ProductLine productLine,
                         String productionBatch, boolean placeholder) {
            UUID id = UUID.randomUUID();
            store.put(id, new ColorChart(id, code, version, "Bang mau test", productLine,
                    productionBatch, null, status, placeholder, "D65", "2", "CIEDE2000",
                    Map.of("kL", 2, "kC", 1, "kH", 1), ChartSource.MANUAL_HEX, null, null, null, null,
                    null, NOW, NOW));
            return id;
        }

        @Override
        public ColorChart save(ColorChart chart) {
            if (throwOnNextActiveSave && chart.getStatus() == ChartStatus.ACTIVE) {
                throw new DataIntegrityViolationException("uq_color_chart_active_per_batch");
            }
            store.put(chart.getId(), chart);
            return chart;
        }

        @Override
        public Optional<ColorChart> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<ColorChart> findByCodeAndVersion(String code, int version) {
            return store.values().stream()
                    .filter(c -> c.getCode().equals(code) && c.getVersion() == version)
                    .findFirst();
        }

        @Override
        public Optional<ColorChart> findActiveByProductLineAndBatch(ProductLine productLine, String productionBatch) {
            return store.values().stream()
                    .filter(c -> c.getStatus() == ChartStatus.ACTIVE)
                    .findFirst();
        }

        @Override
        public boolean existsActiveByProductLineAndBatch(ProductLine productLine, String productionBatch) {
            return findActiveByProductLineAndBatch(productLine, productionBatch).isPresent();
        }

        @Override
        public PageResult<ColorChart> findAllByStatus(ChartStatus status, int page, int size) {
            List<ColorChart> items = store.values().stream().filter(c -> c.getStatus() == status).toList();
            return PageResult.of(items, page, size, items.size());
        }

        @Override
        public PageResult<ColorChart> findAll(int page, int size) {
            return PageResult.of(List.copyOf(store.values()), page, size, store.size());
        }
    }

    private static final class FakePointRepository implements ColorChartPointRepository {

        private final Map<UUID, List<ColorChartPoint>> store = new LinkedHashMap<>();

        void seedValidPoints(UUID chartId) {
            List<ColorChartPoint> list = new ArrayList<>();
            double[] ph = {6.0, 6.3, 6.6, 6.9};
            double[] labL = {85, 65, 45, 25};
            for (int i = 0; i < ph.length; i++) {
                list.add(new ColorChartPoint(UUID.randomUUID(), chartId,
                        BigDecimal.valueOf(ph[i]), i,
                        BigDecimal.valueOf(labL[i]), BigDecimal.valueOf(10 * i), BigDecimal.valueOf(10 * i),
                        new BigDecimal("4.00"), "#AABBCC", "#AABBCC", "muc " + i, "level " + i,
                        0, null, NOW, NOW));
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
        public void saveAll(Iterable<ColorChartPoint> toSave) {
            toSave.forEach(this::save);
        }
    }
}
