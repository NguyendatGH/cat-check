package com.catcheck.identity.domain.port;

import java.time.Instant;
import java.util.UUID;

/**
 * Cong ghi lai ticket {@code PASSWORD_RESET} da dung vao {@code password_reset} (p4 §A5).
 *
 * <p>Luong hien tai theo p11 §11.2.6 la OTP + {@code otp_ticket}, KHONG phai magic
 * link. Bang nay giu lai bang chung: {@code tokenHash} = SHA-256 cua ticket,
 * {@code consumedAt} = luc doi mat khau. Xem {@code docs/handovers/A1.md}.</p>
 */
public interface PasswordResetRepository {

    void insert(UUID userId, String tokenHash, Instant expiresAt, String requestIp);

    void markConsumed(String tokenHash, Instant consumedAt);

    boolean isConsumed(String tokenHash);
}
