package com.catcheck.scan.application;

import com.catcheck.scan.domain.ScanThresholds;
import com.catcheck.scan.domain.color.ChartCatalog;
import org.springframework.stereotype.Service;

@Service
public class ScanConfigService {

    private final ChartCatalog chartCatalog;

    public ScanConfigService(ChartCatalog chartCatalog) {
        this.chartCatalog = chartCatalog;
    }

    public record ActiveChartSummary(String code, int version, boolean isPlaceholder) {
    }

    public record Snapshot(
            int maxEdgePx,
            int minEdgePx,
            double jpegQuality,
            int cropMarginPct,
            long maxBytes,
            String[] acceptedTypes,
            boolean requireCalibratedChart,
            double blurVarMin,
            double meanLumaMin,
            double meanLumaMax,
            double clipHighMax,
            double tiltDegMax,
            double minResultConfidence,
            ActiveChartSummary activeChart
    ) {
    }

    public Snapshot snapshot() {
        ActiveChartSummary chartSummary = chartCatalog
                .findActiveChart(ScanThresholds.DEFAULT_PRODUCT_LINE, null)
                .map(chart -> new ActiveChartSummary(chart.id(), chart.version(), chart.isPlaceholder()))
                .orElse(new ActiveChartSummary(null, 0, true));

        return new Snapshot(
                ScanThresholds.MAX_EDGE_PX,
                ScanThresholds.MIN_EDGE_PX,
                ScanThresholds.JPEG_QUALITY,
                ScanThresholds.CROP_MARGIN_PCT,
                ScanThresholds.MAX_UPLOAD_BYTES,
                ScanThresholds.ACCEPTED_MIME_TYPES,
                false,
                ScanThresholds.BLUR_VAR_MIN,
                ScanThresholds.MEAN_LUMA_MIN,
                ScanThresholds.MEAN_LUMA_MAX,
                ScanThresholds.CLIP_HIGH_MAX,
                ScanThresholds.TILT_DEG_MAX,
                ScanThresholds.MIN_RESULT_CONFIDENCE,
                chartSummary);
    }
}
