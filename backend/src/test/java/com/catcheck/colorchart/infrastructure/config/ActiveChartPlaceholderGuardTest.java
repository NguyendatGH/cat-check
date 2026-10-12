package com.catcheck.colorchart.infrastructure.config;

import com.catcheck.colorchart.domain.ChartSource;
import com.catcheck.colorchart.domain.ChartStatus;
import com.catcheck.colorchart.domain.ColorChart;
import com.catcheck.colorchart.domain.PageResult;
import com.catcheck.colorchart.domain.ProductLine;
import com.catcheck.colorchart.domain.port.ColorChartRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActiveChartPlaceholderGuardTest {

    private static final Instant NOW = Instant.parse("2026-10-09T00:00:00Z");

    @Test
    void doesNotThrowWhenActiveChartIsNotPlaceholder() {
        FakeChartRepository repo = new FakeChartRepository();
        repo.add(ChartStatus.ACTIVE, false);
        ActiveChartPlaceholderGuard guard = new ActiveChartPlaceholderGuard(repo);

        assertDoesNotThrow(() -> guard.run(new DefaultApplicationArguments()));
    }

    @Test
    void throwsIllegalStateWhenActiveChartIsPlaceholder() {
        FakeChartRepository repo = new FakeChartRepository();
        repo.add(ChartStatus.ACTIVE, true);
        ActiveChartPlaceholderGuard guard = new ActiveChartPlaceholderGuard(repo);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> guard.run(new DefaultApplicationArguments()));
        assertTrue(ex.getMessage().contains("PLACEHOLDER"), "Thong diep phai neu ro la placeholder");
    }

    @Test
    void ignoresArchivedOrDraftPlaceholdersThatAreNotActive() {
        FakeChartRepository repo = new FakeChartRepository();
        repo.add(ChartStatus.ARCHIVED, true);
        repo.add(ChartStatus.DRAFT, true);
        repo.add(ChartStatus.ACTIVE, false);
        ActiveChartPlaceholderGuard guard = new ActiveChartPlaceholderGuard(repo);

        assertDoesNotThrow(() -> guard.run(new DefaultApplicationArguments()));
    }

    @Test
    void checksEveryPageWhenActiveChartsSpanMultiplePages() {
        FakeChartRepository repo = new FakeChartRepository();
        for (int i = 0; i < 150; i++) {
            repo.add(ChartStatus.ACTIVE, false);
        }
        repo.add(ChartStatus.ACTIVE, true);
        ActiveChartPlaceholderGuard guard = new ActiveChartPlaceholderGuard(repo);

        assertThrows(IllegalStateException.class, () -> guard.run(new DefaultApplicationArguments()));
    }

    private static final class FakeChartRepository implements ColorChartRepository {

        private final List<ColorChart> store = new ArrayList<>();

        void add(ChartStatus status, boolean placeholder) {
            UUID id = UUID.randomUUID();
            store.add(new ColorChart(id, "CHART-" + id, 1, "Bang mau test", ProductLine.STANDARD,
                    null, null, status, placeholder, "D65", "2", "CIEDE2000",
                    Map.of("kL", 2), ChartSource.MANUAL_HEX, null, null, null, null,
                    null, NOW, NOW));
        }

        @Override
        public Optional<ColorChart> findById(UUID id) {
            throw new UnsupportedOperationException("khong dung trong test nay");
        }

        @Override
        public Optional<ColorChart> findByCodeAndVersion(String code, int version) {
            throw new UnsupportedOperationException("khong dung trong test nay");
        }

        @Override
        public Optional<ColorChart> findActiveByProductLineAndBatch(ProductLine productLine, String productionBatch) {
            throw new UnsupportedOperationException("khong dung trong test nay");
        }

        @Override
        public boolean existsActiveByProductLineAndBatch(ProductLine productLine, String productionBatch) {
            throw new UnsupportedOperationException("khong dung trong test nay");
        }

        @Override
        public PageResult<ColorChart> findAllByStatus(ChartStatus status, int page, int size) {
            List<ColorChart> matching = store.stream().filter(c -> c.getStatus() == status).toList();
            int from = Math.min(page * size, matching.size());
            int to = Math.min(from + size, matching.size());
            return PageResult.of(matching.subList(from, to), page, size, matching.size());
        }

        @Override
        public PageResult<ColorChart> findAll(int page, int size) {
            throw new UnsupportedOperationException("khong dung trong test nay");
        }

        @Override
        public ColorChart save(ColorChart chart) {
            throw new UnsupportedOperationException("khong dung trong test nay");
        }
    }
}
