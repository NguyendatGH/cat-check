package com.catcheck.identity.domain.port;

import com.catcheck.identity.domain.UserRole;

import java.util.List;
import java.util.UUID;

/**
 * Cong vao {@code user_role}. Trien khai o {@code ..infrastructure.persistence} (R7).
 */
public interface UserRoleRepository {

    List<UserRole> findByUserId(UUID userId);

    /**
     * Cap role. {@code onConflictDoNothing} de giam idempotent: cap lai role da co
     * khong phai loi, va khong ghi deo {@code granted_at} goc.
     */
    void grant(UUID userId, UserRole role, UUID grantedBy);

    void revoke(UUID userId, UserRole role);
}
