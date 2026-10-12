package com.catcheck.identity.api.dto;

import com.catcheck.identity.application.AdminUserService;

import java.time.Instant;
import java.util.UUID;

public record AdminUnmaskResponse(UUID userId, String email, String phone, Instant unmaskedUntil) {

    public static AdminUnmaskResponse from(AdminUserService.UnmaskedPii pii) {
        return new AdminUnmaskResponse(pii.userId(), pii.email(), pii.phone(), pii.unmaskedUntil());
    }
}
