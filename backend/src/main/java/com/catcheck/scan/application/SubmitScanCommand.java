package com.catcheck.scan.application;

import com.catcheck.scan.domain.CaptureSource;
import com.catcheck.scan.domain.ScanAssignment;
import com.catcheck.scan.domain.color.port.VisionEngine;
import com.catcheck.scan.domain.port.RawImagePort;

import java.time.Instant;
import java.util.UUID;

public record SubmitScanCommand(
        UUID userId,
        UUID catId,
        ScanAssignment assignment,
        Instant capturedAt,
        CaptureSource captureSource,
        String deviceHint,
        String idempotencyKey,
        RawImagePort.RoiRect roi,
        VisionEngine.QuadHint cardQuadHint,
        byte[] imageBytes
) {

    public SubmitScanCommand {
        if (userId == null) {
            throw new IllegalArgumentException("submitScanCommand.userId là bắt buộc");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("submitScanCommand.idempotencyKey là bắt buộc");
        }
        if (imageBytes == null || imageBytes.length == 0) {
            throw new IllegalArgumentException("submitScanCommand.imageBytes là bắt buộc");
        }
        roi = roi == null ? RawImagePort.RoiRect.fullFrame() : roi;
        cardQuadHint = cardQuadHint == null ? VisionEngine.QuadHint.empty() : cardQuadHint;
    }
}
