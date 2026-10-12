package com.catcheck.scan.application;

import java.time.Instant;
import java.util.UUID;

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
