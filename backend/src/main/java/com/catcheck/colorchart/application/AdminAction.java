package com.catcheck.colorchart.application;

import java.util.UUID;

public record AdminAction(
        UUID actorId,
        String actorRole,
        String reason,
        String requestId,
        String ipAddress,
        String userAgent
) {
}
