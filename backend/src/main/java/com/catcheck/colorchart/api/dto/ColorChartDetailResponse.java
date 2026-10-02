package com.catcheck.colorchart.api.dto;

import com.catcheck.colorchart.application.ColorChartAdminService.ColorChartDetail;

import java.util.List;

/**
 * Chi tiết bảng màu + toàn bộ điểm (L29).
 */
public record ColorChartDetailResponse(
        String id,
        String code,
        int version,
        String name,
        String productLine,
        String productionBatch,
        String status,
        boolean placeholder,
        String source,
        List<ColorChartPointResponse> points) {

    public static ColorChartDetailResponse from(ColorChartDetail detail) {
        return new ColorChartDetailResponse(
                detail.chart().getId().toString(),
                detail.chart().getCode(),
                detail.chart().getVersion(),
                detail.chart().getName(),
                detail.chart().getProductLine() != null ? detail.chart().getProductLine().name() : null,
                detail.chart().getProductionBatch(),
                detail.chart().getStatus().name(),
                detail.chart().isPlaceholder(),
                detail.chart().getSource().name(),
                detail.points().stream().map(ColorChartPointResponse::from).toList());
    }
}
