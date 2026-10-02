package com.catcheck.identity.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Anh chup {@code user_mfa_totp} (p4 §A7).
 *
 * <p>{@code secretEnc} la ciphertext AES-256-GCM khoa con {@code mfa.totp} — KHONG bao
 * gio doc thang vao day ma khong giai ma. KHONG vao {@code audit_log.before/after},
 * KHONG vao ban xuat DSAR (p4 §A7).</p>
 *
 * <p>{@code PENDING} nghia la da sinh secret nhung CHUA xac nhan ma lan dau tien —
 * coi nhu chua bat (p8 {@code TOTP_SETUP_REQUIRED}).</p>
 */
public record MfaTotp(
        UUID userId,
        byte[] secretEnc,
        TotpStatus status,
        String algorithm,
        int digits,
        int periodSeconds,
        Long lastUsedStep,
        int failedCount,
        Instant lockedUntil,
        Instant pendingExpiresAt,
        Instant activatedAt,
        int keyVersion,
        UUID resetBy,
        Instant resetAt,
        Instant createdAt,
        Instant updatedAt) {

    public boolean isActive() {
        return status == TotpStatus.ACTIVE;
    }
}
