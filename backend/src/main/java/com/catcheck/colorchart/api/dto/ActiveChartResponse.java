package com.catcheck.colorchart.api.dto;

import com.catcheck.colorchart.domain.ColorChart;

/**
 * Bảng màu đang {@code ACTIVE} — F5 ({@code GET /reference/color-charts/active}).
 *
 * <p><b>Không</b> trả toạ độ Lab của từng điểm — đó là tài sản hiệu chuẩn (p8 §8.4.6). Pipeline
 * scan lấy Lab qua {@code ChartCatalog} (cổng nội bộ).
 */
public record ActiveChartResponse(
        String code,
        int version,
        boolean placeholder,
        String productLine) {

    public static ActiveChartResponse from(ColorChart chart) {
        return new ActiveChartResponse(
                chart.getCode(),
                chart.getVersion(),
                chart.isPlaceholder(),
                chart.getProductLine() != null ? chart.getProductLine().name() : null);
    }
}
