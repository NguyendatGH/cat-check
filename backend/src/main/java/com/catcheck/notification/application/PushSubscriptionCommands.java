package com.catcheck.notification.application;

import java.util.UUID;

public final class PushSubscriptionCommands {

    private PushSubscriptionCommands() {
    }

    public record Upsert(
            UUID userId,
            String installationId,
            String legacyToken,
            String platform,
            String deviceLabel,
            String userAgent) {
    }
}
