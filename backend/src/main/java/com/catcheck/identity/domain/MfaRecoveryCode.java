package com.catcheck.identity.domain;

import java.time.Instant;
import java.util.UUID;

public record MfaRecoveryCode(
        UUID id,
        UUID userId,
        String codeHash,
        int pepperVersion,
        UUID batchId,
        Instant usedAt,
        String usedIp,
        Instant createdAt) {
}
