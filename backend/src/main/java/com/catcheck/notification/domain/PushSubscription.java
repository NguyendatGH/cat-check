package com.catcheck.notification.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Một trình duyệt/thiết bị đang nhận web push — bảng {@code push_subscription} (p4 F3).
 *
 * <p>Tên {@code device_token}/{@code fcm_device_token} nằm trong danh sách tên BỊ CẤM của
 * p4 §4.1.7: FCM định danh thiết bị bằng <b>FID</b> (bền), còn registration token thì xoay theo
 * thời gian — mỗi lần xoay sẽ sinh một dòng mới cho cùng một trình duyệt và hệ thống gửi push
 * trùng.</p>
 *
 * @param installationId Firebase Installation ID ({@code fid}) — đường mới, ưu tiên
 * @param legacyToken    registration token — đường cũ, tương thích ngược; sẽ deprecate
 * @param userAgent      UA thô, nguồn để sinh {@code deviceLabel}
 */
public record PushSubscription(
        UUID id,
        UUID userId,
        String installationId,
        String legacyToken,
        PushPlatform platform,
        String deviceLabel,
        String userAgent,
        Instant lastSeenAt,
        Instant lastSuccessAt,
        Instant lastErrorAt,
        int failureCount,
        Instant revokedAt,
        PushRevokeReason revokeReason,
        Instant createdAt) {

    public PushSubscription {
        if (installationId == null && legacyToken == null) {
            throw new IllegalArgumentException(
                    "push_subscription phải có ít nhất fid hoặc legacy_token (ck_push_identifier)");
        }
    }

    public boolean active() {
        return revokedAt == null;
    }

    /**
     * Khoá định danh thiết bị, đúng biểu thức của unique index
     * {@code (user_id, COALESCE(fid, legacy_token))} — p12 §12.3.2 "có fid → gửi theo FID;
     * không có → theo legacy_token".
     */
    public String deviceKey() {
        return installationId != null ? installationId : legacyToken;
    }

    public boolean usesInstallationId() {
        return installationId != null;
    }
}
