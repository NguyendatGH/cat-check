package com.catcheck.identity.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Anh chup {@code user_mfa_recovery_code} (p4 §A8).
 *
 * <p>{@code codeHash} la HMAC-SHA256 hex cua ma 10 ky tu Crockford — khong phai
 * BCrypt (p11 §11.12.3 ghi de p4: BCrypt co salt ngau nhien nen khong danh
 * {@code UNIQUE(code_hash)} duoc). Ma tho chi ton tai trong response dung mot lan
 * khi enroll/reset (p8 §8.5.2).</p>
 */
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
