package com.catcheck.identity.domain.port;

import com.catcheck.identity.domain.AdminUserSummary;
import com.catcheck.identity.domain.UserStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminUserQueryPort {

    List<AdminUserSummary> search(UserStatus status, String email, String highestPackage,
                                  int offset, int limit);

    long count(UserStatus status, String email, String highestPackage);

    Optional<AdminUserSummary> findById(UUID userId);
}
