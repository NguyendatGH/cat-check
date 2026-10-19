package com.catcheck.colorchart.api.dto;

import com.catcheck.colorchart.domain.ColorChart;

public record ColorChartSummaryResponse(
        String id,
        String code,
        int version,
        String name,
        String productLine,
        String productionBatch,
        String status,
        boolean placeholder,
        String source) {

    public static ColorChartSummaryResponse from(ColorChart chart) {
        return new ColorChartSummaryResponse(
                chart.getId().toString(),
                chart.getCode(),
                chart.getVersion(),
                chart.getName(),
                chart.getProductLine() != null ? chart.getProductLine().name() : null,
                chart.getProductionBatch(),
                chart.getStatus().name(),
                chart.isPlaceholder(),
                chart.getSource().name());
    }
}
