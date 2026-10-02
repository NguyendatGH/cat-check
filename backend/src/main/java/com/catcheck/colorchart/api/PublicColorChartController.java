package com.catcheck.colorchart.api;

import com.catcheck.colorchart.api.dto.ActiveChartResponse;
import com.catcheck.colorchart.api.dto.PhBandResponse;
import com.catcheck.colorchart.application.ColorChartQueryService;
import com.catcheck.colorchart.domain.PhClassificationBand;
import com.catcheck.colorchart.domain.ProductLine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Danh mục công khai — F1, F5 (p8 §8.4.6).
 *
 * <p>Cả hai endpoint đều {@code public, max-age=300} (p8 §8.1.10) — dữ liệu cấu hình ít đổi và
 * client cần cache để màn hình không phải gọi lại mỗi lần vào app.
 */
@RestController
@RequestMapping("/api/v1/reference")
@Tag(name = "Danh mục công khai", description = "F1, F5 — dải phân loại pH và bảng màu đang ACTIVE")
public class PublicColorChartController {

    private final ColorChartQueryService queryService;

    public PublicColorChartController(ColorChartQueryService queryService) {
        this.queryService = queryService;
    }

    @Operation(
            operationId = "listPhBands",
            summary = "Dải phân loại pH",
            description = "F1. Nguồn duy nhất cho PhGaugeBar/PhBadge (p9 §9.2.4). "
                    + "6 dải toàn cục từ ph_classification_band.")
    @GetMapping("/ph-bands")
    public ResponseEntity<List<PhBandResponse>> listPhBands(
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, defaultValue = "vi") String acceptLanguage) {
        Locale locale = Locale.forLanguageTag(acceptLanguage);
        List<PhBandResponse> bands = queryService.findGlobalBands().stream()
                .map(band -> PhBandResponse.from(band, locale.getLanguage()))
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                .body(bands);
    }

    @Operation(
            operationId = "getActiveColorChart",
            summary = "Bảng màu đang ACTIVE",
            description = "F5. Chỉ trả metadata — KHÔNG trả toạ độ Lab (tài sản hiệu chuẩn, p8 §8.4.6).")
    @GetMapping("/color-charts/active")
    public ResponseEntity<ActiveChartResponse> getActiveColorChart(
            @RequestParam(required = false) String productLine,
            @RequestParam(required = false) String productionBatch) {
        ProductLine line = productLine != null && !productLine.isBlank()
                ? ProductLine.valueOf(productLine)
                : ProductLine.STANDARD;
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                .body(ActiveChartResponse.from(queryService.findActiveChart(line, productionBatch)));
    }
}
