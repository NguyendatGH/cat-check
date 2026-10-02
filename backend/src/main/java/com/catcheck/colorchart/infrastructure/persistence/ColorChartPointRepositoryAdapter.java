package com.catcheck.colorchart.infrastructure.persistence;

import com.catcheck.colorchart.domain.ColorChartPoint;
import com.catcheck.colorchart.domain.port.ColorChartPointRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Bộ chuyển đổi từ cổng {@link ColorChartPointRepository} sang Spring Data (R2).
 */
@Repository
class ColorChartPointRepositoryAdapter implements ColorChartPointRepository {

    private final ColorChartPointJpaRepository jpaRepository;

    ColorChartPointRepositoryAdapter(ColorChartPointJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<ColorChartPoint> findByChartIdOrderBySortOrder(UUID chartId) {
        return jpaRepository.findByChartIdOrderBySortOrder(chartId);
    }

    @Override
    public int deleteByChartId(UUID chartId) {
        return jpaRepository.deleteByChartId(chartId);
    }

    @Override
    public ColorChartPoint save(ColorChartPoint point) {
        return jpaRepository.save(point);
    }

    @Override
    public void saveAll(Iterable<ColorChartPoint> points) {
        jpaRepository.saveAll(points);
    }
}
