package com.catcheck.colorchart.api.dto;

import com.catcheck.colorchart.domain.ColorChart;

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
