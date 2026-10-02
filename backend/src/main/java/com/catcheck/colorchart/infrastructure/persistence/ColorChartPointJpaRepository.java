package com.catcheck.colorchart.infrastructure.persistence;

import com.catcheck.colorchart.domain.ColorChartPoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data repository cho {@code color_chart_point} (R7).
 */
interface ColorChartPointJpaRepository extends JpaRepository<ColorChartPoint, UUID> {

    List<ColorChartPoint> findByChartIdOrderBySortOrder(UUID chartId);

    int deleteByChartId(UUID chartId);
}
