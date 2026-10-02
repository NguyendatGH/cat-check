package com.catcheck.identity.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Anh chup cua {@code email_otp} (p4 §A3 + 4 cot bo sung theo p11 §11.2.6).
 *
 * <p>{@code codeHash} la HMAC-SHA256 hex 64 ky tu, KHONG phai ma tho. Khi so sanh
 * phai dung {@code MessageDigest.isEqual} chu khong dung {@code String.equals}
 * (p11 §11.2.1).</p>
 */
public record EmailOtpChallenge(
        UUID id,
        UUID userId,
        String email,
        String codeHash,
        int pepperVersion,
        OtpPurpose purpose,
        Instant expiresAt,
        int attemptCount,
        int maxAttempts,
        Instant consumedAt,
        String requestIp,
        Instant verifiedAt,
        String ticketHash,
        Instant ticketExpiresAt,
        Instant createdAt) {

    /** Challenge con dung duoc de nhan ma. */
    public boolean isUsableAt(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now) && attemptCount < maxAttempts;
    }

    /**
     * p11 §11.2.1: sai lan thu 5 -> het luot, phai gui lai ma moi.
     * Kiem tra {@code >=} chu khong phai {@code >} neu phia tang doi dat
     * {@code attempt_count} truoc khi so sanh.
     */
    public boolean hasAttemptsLeft() {
        return attemptCount < maxAttempts;
    }

    public boolean isTicketValidAt(Instant now) {
        return ticketHash != null && verifiedAt != null
                && consumedAt == null
                && ticketExpiresAt != null
                && ticketExpiresAt.isAfter(now);
    }
}
