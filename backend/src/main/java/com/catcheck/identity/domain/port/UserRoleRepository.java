package com.catcheck.identity.domain.port;

import com.catcheck.identity.domain.UserRole;

import java.util.List;
import java.util.UUID;

public interface UserRoleRepository {

    List<UserRole> findByUserId(UUID userId);

    void grant(UUID userId, UserRole role, UUID grantedBy);

    void revoke(UUID userId, UserRole role);
}
