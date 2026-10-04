package com.catcheck.identity.api.dto;

import com.catcheck.identity.domain.DeviceSession;
import com.catcheck.shared.security.PiiMask;

import java.time.Instant;
import java.util.UUID;

/**
 * Một phiên đang mở của người dùng — L11 {@code GET /admin/users/{userId}/sessions}.
 *
 * <p>KHÔNG có {@code sessionIdHash}: đó là khoá dùng để thu hồi đúng một phiên, và nó cũng là
 * SHA-256 của session id thật (p4 §A6) — không có lý do nào để nó rời khỏi server. {@code id} ở
 * đây là khoá chính của {@code user_device_session}, đủ để L12 làm việc.</p>
 *
 * <p>{@code ipAddressMasked}: p15 REQ-RBAC-01 che PII trong admin panel, và IP là dữ liệu cá
 * nhân theo Nghị định 13. Giữ phần mạng để tổng đài phân biệt được "vẫn máy quen" với "nơi
 * khác".</p>
 */
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
