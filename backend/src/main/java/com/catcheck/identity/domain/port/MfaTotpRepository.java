package com.catcheck.identity.domain.port;

import com.catcheck.identity.domain.MfaTotp;
import com.catcheck.identity.domain.TotpStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Cong vao {@code user_mfa_totp}. Trien khai o {@code ..infrastructure.persistence} (R7).
 */
public interface MfaTotpRepository {

    Optional<MfaTotp> findByUserId(UUID userId);

    void insert(MfaTotp totp);

    /** Chuyen {@code PENDING -> ACTIVE} khi xac nhan ma lan dau tung dung. */
    void markActive(UUID userId, Instant activatedAt, long lastUsedStep);

    /** Cap nhat {@code last_used_step} sau moi lan dung thanh cong (chong replay). */
    void updateLastUsedStep(UUID userId, long step);

    /** Tang {@code failed_count}; tra ve gia tri sau khi tang. */
    int incrementFailedCount(UUID userId);

    void resetFailedCount(UUID userId);

    void lock(UUID userId, Instant lockedUntil);

    /** Xoa dong — dung khi user tu go TOTP hoac reset hai nguoi. */
    void delete(UUID userId);

    long countActive();
}
