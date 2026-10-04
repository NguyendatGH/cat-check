package com.catcheck.notification.domain;

import java.util.UUID;

/**
 * Một đích gửi push đã phân giải: hoặc FID (đường mới) hoặc registration token (đường cũ).
 *
 * @param subscriptionId để map kết quả FCM trở lại đúng dòng {@code notification_outbox}
 */
public record PushTarget(UUID subscriptionId, String installationId, String legacyToken) {

    public PushTarget {
        if (installationId == null && legacyToken == null) {
            throw new IllegalArgumentException("pushTarget phải có fid hoặc legacyToken");
        }
    }

    public boolean byInstallationId() {
        return installationId != null;
    }

    /** Chuỗi truyền cho FCM: {@code addAllFids(...)} nếu có FID, ngược lại {@code addAllTokens(...)}. */
    public String value() {
        return installationId != null ? installationId : legacyToken;
    }
}
