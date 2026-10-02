package com.catcheck.identity.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Anh chup cua {@code user_device_session} (p4 §A4b) — phien dang nhap ma nguoi dung
 * nhin thay trong man hinh "Thiet bi dang dang nhap".
 *
 * <p>Bang nay KHONG chua token va KHONG tham gia xac thuc: Spring Session
 * ({@code SPRING_SESSION}) moi la noi luu phien thật. {@code sessionIdHash} la
 * SHA-256 cua {@code SPRING_SESSION.SESSION_ID} chi de noi 1-1 va goi dung phien
 * khi thu hoi.</p>
 */
public record DeviceSession(
        UUID id,
        UUID userId,
        String sessionIdHash,
        String deviceLabel,
        String userAgent,
        String ipAddress,
        boolean rememberMe,
        Instant createdAt,
        Instant lastSeenAt,
        Instant expiresAt,
        Instant revokedAt,
        SessionRevokeReason revokeReason) {

    public boolean isActiveAt(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    /** Phien dang chay chinh cua browser hien tai — KHONG cho thu hoi qua API (p8 A7). */
    public boolean isCurrent(String currentSessionIdHash) {
        return sessionIdHash.equals(currentSessionIdHash);
    }
}
