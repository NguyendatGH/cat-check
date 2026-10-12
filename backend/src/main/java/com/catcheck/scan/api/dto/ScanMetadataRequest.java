package com.catcheck.scan.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

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
