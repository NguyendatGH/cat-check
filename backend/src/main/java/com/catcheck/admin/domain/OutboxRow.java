package com.catcheck.admin.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Một bản ghi chờ gửi — hợp nhất {@code email_outbox} và {@code notification_outbox} (p4 nhóm F)
 * cho L66 {@code GET /admin/notifications/outbox}.
 *
 * <p>p8 L66 yêu cầu một endpoint cho CẢ HAI bảng. Hai bảng có cột khác nhau nên record này giữ
 * phần giao nhau ({@code status}, {@code attempts}, {@code nextAttemptAt}, {@code lastError},
 * {@code sentAt}) và quy phần riêng về hai trường chung:</p>
 * <ul>
 *   <li>{@code reference} — {@code template_code} với email, {@code notification_id} với push;</li>
 *   <li>{@code recipient} — địa chỉ email <b>(PII, phải mask trước khi ra response)</b> với
 *       email, {@code push_subscription_id} với push.</li>
 * </ul>
 *
 * @param channel {@code EMAIL} hoặc {@code PUSH} — không phải enum DB (hai bảng riêng, không có
 *                cột nào mang giá trị này), nên đây là giá trị do tầng đọc gán
 */
public record OutboxRow(
        UUID id,
        String channel,
        String status,
        String reference,
        String recipient,
        int attempts,
        Instant nextAttemptAt,
        String lastError,
        Instant sentAt,
        Instant createdAt
) {

    public static final String CHANNEL_EMAIL = "EMAIL";
    public static final String CHANNEL_PUSH = "PUSH";

    /** Người nhận có phải địa chỉ email (tức PII cần mask) hay không. */
    public boolean recipientIsEmail() {
        return CHANNEL_EMAIL.equals(channel);
    }
}
