package com.catcheck.identity.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Anh chup cua {@code user_identity} (p4 §A2): mot phuong thuc dang nhap cua mot tai khoan.
 *
 * <p>LOCAL: {@code providerUserId} chua email da chuan hoa.
 * GOOGLE: {@code providerUserId} chua claim {@code sub} — KHONG phai email, vi
 * Google cho doi email va dung email se mo duong chiem tai khoan (p4 §A2).</p>
 */
public record UserIdentity(
        UUID id,
        UUID userId,
        IdentityProvider provider,
        String providerUserId,
        String providerEmail,
        boolean emailVerified,
        String passwordHash,
        Instant linkedAt,
        Instant lastUsedAt,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * {@code password_hash} luu kem prefix {@code {bcrypt}} de
     * {@code DelegatingPasswordEncoder} doi thuat toan duoc sau ma khong phai
     * migrate lai toan bo hang (p11 S4).
     */
    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isBlank();
    }

    public static String bcryptPrefix() {
        return "{bcrypt}";
    }
}
