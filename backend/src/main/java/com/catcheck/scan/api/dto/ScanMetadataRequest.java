package com.catcheck.scan.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

/**
 * Part {@code metadata} (application/json) của {@code POST /api/v1/scans} (p8 §8.5.4).
 *
 * <p>{@code clientPrecheck}/{@code clientTimings} nhận nguyên khối JSON tự do (chỉ để log/quan
 * sát) — server KHÔNG tin metric của client (p6 mục a: "Server làm toàn bộ colorimetry, và không
 * tin metric của client").</p>
 */
public record ScanMetadataRequest(
        @NotBlank String scanRequestId,
        String catId,
        @NotBlank String assignment,
        @NotNull Instant capturedAt,
        String captureSource,
        RoiInput roi,
        Boolean cardDetected,
        double[][] cardQuadHint,
        Map<String, Object> clientPrecheck,
        Map<String, Object> clientTimings,
        String deviceHint,
        Integer imageWidth,
        Integer imageHeight,
        Double jpegQuality
) {
}
