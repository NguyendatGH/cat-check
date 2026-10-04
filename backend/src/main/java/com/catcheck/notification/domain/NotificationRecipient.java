package com.catcheck.notification.domain;

import java.time.ZoneId;
import java.util.UUID;

/**
 * Thông tin tối thiểu về người nhận, đọc từ {@code app_user} (p4 A1).
 *
 * <p><b>{@code emailAddress} là PII.</b> Chỉ dùng để điền {@code email_outbox.to_address};
 * KHÔNG log (p17 §17.10b, ArchUnit R16).</p>
 *
 * @param timezone IANA zone của user — quiet hours tính theo giờ địa phương (p12 §12.5.2)
 */
public record NotificationRecipient(UUID userId, String emailAddress, String locale, ZoneId timezone) {

    public NotificationRecipient {
        if (userId == null) {
            throw new IllegalArgumentException("recipient.userId không được null");
        }
        locale = (locale == null || locale.isBlank()) ? "vi" : locale;
        timezone = timezone == null ? ZoneId.of("Asia/Ho_Chi_Minh") : timezone;
    }
}
