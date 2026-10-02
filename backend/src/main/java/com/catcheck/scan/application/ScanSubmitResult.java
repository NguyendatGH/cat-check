package com.catcheck.scan.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Kết quả {@code POST /api/v1/scans} — {@code scanId} là {@code null} khi và chỉ khi không có
 * bản ghi mà {@code GET /scans/{id}} đọc được (p8 §8.5.4, áp dụng cho {@code INCONCLUSIVE}).
 */
public record ScanSubmitResult(
        UUID scanId,
        String scanRequestId,
        UUID catId,
        String catName,
        String assignment,
        Instant capturedAt,
        String status,
        PipelineOutcome outcome,
        boolean creditCharged,
        Integer creditBalanceAfter,
        boolean isTrial,
        boolean imageStored,
        Instant imageRetainedUntil,
        String storeImageReason,
        Instant reassignableUntil,
        int reassignRemaining,
        boolean idempotentReplay
) {
}
