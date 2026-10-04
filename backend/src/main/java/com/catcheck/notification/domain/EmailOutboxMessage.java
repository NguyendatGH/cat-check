package com.catcheck.notification.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Một email chờ gửi — bảng {@code email_outbox} (p4 F5).
 *
 * <p>p12 §12.4: service nghiệp vụ chỉ {@code INSERT} vào bảng này <b>trong cùng transaction</b>
 * với sự kiện nghiệp vụ rồi trả {@code 202} ngay; {@code SendEmailOutboxJob} đẩy đi sau. Cấm
 * gọi SMTP trong request thread.</p>
 *
 * @param toAddress không FK sang {@code app_user}: email OTP gửi TRƯỚC khi user tồn tại
 * @param payload   biến truyền vào template
 */
public record EmailOutboxMessage(
        UUID id,
        String toAddress,
        String templateCode,
        String locale,
        Map<String, Object> payload,
        OutboxStatus status,
        int attempts,
        Instant nextAttemptAt,
        String lastError,
        String dedupeKey,
        Instant sentAt,
        Instant createdAt) {

    public EmailOutboxMessage {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
