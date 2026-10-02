package com.catcheck.identity.domain.port;

import com.catcheck.identity.domain.MfaRecoveryCode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cong vao {@code user_mfa_recovery_code}. Trien khai o {@code ..infrastructure.persistence} (R7).
 */
public interface MfaRecoveryCodeRepository {

    void insertBatch(UUID userId, List<MfaRecoveryCode> codes);

    /** Ma chua dung cua dung user — verify bang cach duyet toi da 10 dong (p4 §A8). */
    List<MfaRecoveryCode> findUnusedByUserId(UUID userId);

    /** Danh dung mot ma: set {@code used_at} + {@code used_ip}. Tra ve {@code true} neu chua ai dung. */
    boolean markUsed(UUID codeId, UUID userId, Instant usedAt, String usedIp);

    /** Dem ma con lai — de bao truong khi con &lt;= 3 (p4 §A8). */
    long countUnusedByUserId(UUID userId);

    /** Huyn tron lo cu khi sinh lai (p11 §11.12.3: giu lan lon la nguon nham lan). */
    int deleteByUserId(UUID userId);
}
