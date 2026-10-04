package com.catcheck.notification.application;

import java.util.UUID;

/** Lệnh của nhóm G9–G11 (p8 §8.4.7). */
public final class PushSubscriptionCommands {

    private PushSubscriptionCommands() {
    }

    /**
     * G10 — upsert theo {@code fid} (hoặc {@code legacyToken}).
     *
     * @param installationId Firebase Installation ID — đường mới, ưu tiên (p12 §12.3.2)
     * @param legacyToken    registration token — đường cũ, cho client chưa nâng SDK
     * @param platform       {@code WEB} / {@code ANDROID_PWA} / {@code IOS_PWA}
     * @param deviceLabel    "Chrome trên Android" — để user nhận ra máy của mình
     */
    public record Upsert(
            UUID userId,
            String installationId,
            String legacyToken,
            String platform,
            String deviceLabel,
            String userAgent) {
    }
}
