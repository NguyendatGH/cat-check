package com.catcheck.scan.application;

import com.catcheck.scan.domain.CaptureSource;
import com.catcheck.scan.domain.QualityFlag;
import com.catcheck.scan.domain.ScanAssignment;
import com.catcheck.scan.domain.ScanClassification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ManualScanCommand(
        UUID userId,
        UUID catId,
        ScanAssignment assignment,
        Instant capturedAt,
        CaptureSource captureSource,
        String deviceHint,
        String idempotencyKey,
        BigDecimal phValue,
        ScanClassification forcedClassification,
        BigDecimal confidence,
        List<QualityFlag> qualityFlags) {

    public ManualScanCommand {
        if (userId == null) {
            throw new IllegalArgumentException("manualScanCommand.userId là bắt buộc");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("manualScanCommand.idempotencyKey là bắt buộc");
        }
        captureSource = captureSource == null ? CaptureSource.CAMERA : captureSource;
        qualityFlags = qualityFlags == null ? List.of() : List.copyOf(qualityFlags);
    }
}
