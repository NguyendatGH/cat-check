package com.catcheck.identity.api.dto;

import com.catcheck.identity.domain.DeviceSession;
import com.catcheck.shared.security.PiiMask;

import java.time.Instant;
import java.util.UUID;

public record AdminSessionItem(
        UUID id,
        String deviceLabel,
        String ipAddressMasked,
        boolean rememberMe,
        Instant createdAt,
        Instant lastSeenAt,
        Instant expiresAt
) {

    public static AdminSessionItem from(DeviceSession session) {
        return new AdminSessionItem(
                session.id(),
                session.deviceLabel(),
                PiiMask.ipAddress(session.ipAddress()),
                session.rememberMe(),
                session.createdAt(),
                session.lastSeenAt(),
                session.expiresAt());
    }
}
