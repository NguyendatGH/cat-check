package com.catcheck.identity.api.dto;

import com.catcheck.identity.application.AdminUserService;

import java.util.UUID;

public record AdminLockResponse(UUID userId, String status, int revokedSessions) {

    public static AdminLockResponse from(AdminUserService.LockResult result) {
        return new AdminLockResponse(result.userId(), result.status().name(), result.revokedSessions());
    }
}
