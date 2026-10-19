package com.catcheck.colorchart.api.dto;

import com.catcheck.colorchart.domain.ColorChartPoint;

public record ColorChartPointResponse(
        String id,
        double phValue,
        int sortOrder,
        double labL,
        double labA,
        double labB,
        double toleranceDeltaE,
        String hexSrgb,
        String displayHex,
        String displayNameVi,
        String displayNameEn) {

    public static ColorChartPointResponse from(ColorChartPoint point) {
        return new ColorChartPointResponse(
                point.getId().toString(),
                point.getPhValue().doubleValue(),
                point.getSortOrder(),
                point.getLabL().doubleValue(),
                point.getLabA().doubleValue(),
                point.getLabB().doubleValue(),
                point.getToleranceDeltaE().doubleValue(),
                point.getHexSrgb(),
                point.getDisplayHex(),
                point.getDisplayNameVi(),
                point.getDisplayNameEn());
    }
}
