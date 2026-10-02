package com.catcheck.colorchart.domain.port;

import com.catcheck.colorchart.domain.ColorChartPoint;

import java.util.List;
import java.util.UUID;

/**
 * Cổng đọc/ghi {@code color_chart_point}.
 */
public interface ColorChartPointRepository {

    List<ColorChartPoint> findByChartIdOrderBySortOrder(UUID chartId);

    int deleteByChartId(UUID chartId);

    ColorChartPoint save(ColorChartPoint point);

    void saveAll(Iterable<ColorChartPoint> points);
}
