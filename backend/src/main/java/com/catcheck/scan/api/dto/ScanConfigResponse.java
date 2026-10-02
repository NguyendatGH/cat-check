package com.catcheck.scan.api.dto;

import com.catcheck.scan.application.ScanConfigService;

/** {@code GET /api/v1/scan/config} (p8 §8.4.5 E12, p6 §6.3.4/§6.3.5). */
public record ScanConfigResponse(
        int maxEdgePx,
        int minEdgePx,
        double jpegQuality,
        int cropMarginPct,
        long maxBytes,
        String[] acceptedTypes,
        boolean requireCalibratedChart,
        PrecheckThresholds precheckThresholds,
        double minResultConfidence,
        ActiveChart activeChart
) {

    public record PrecheckThresholds(
            double blurVarMin, double meanLumaMin, double meanLumaMax,
            double clipHighMax, double tiltDegMax) {
    }

    public record ActiveChart(String code, int version, boolean isPlaceholder) {
    }

    public static ScanConfigResponse from(ScanConfigService.Snapshot s) {
        return new ScanConfigResponse(
                s.maxEdgePx(), s.minEdgePx(), s.jpegQuality(), s.cropMarginPct(), s.maxBytes(),
                s.acceptedTypes(), s.requireCalibratedChart(),
                new PrecheckThresholds(s.blurVarMin(), s.meanLumaMin(), s.meanLumaMax(),
                        s.clipHighMax(), s.tiltDegMax()),
                s.minResultConfidence(),
                new ActiveChart(s.activeChart().code(), s.activeChart().version(), s.activeChart().isPlaceholder()));
    }
}
