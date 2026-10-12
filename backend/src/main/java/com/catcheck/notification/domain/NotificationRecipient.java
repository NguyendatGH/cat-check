package com.catcheck.notification.domain;

import java.time.ZoneId;
import java.util.UUID;

public record NotificationRecipient(UUID userId, String emailAddress, String locale, ZoneId timezone) {

    public NotificationRecipient {
        if (userId == null) {
            throw new IllegalArgumentException("recipient.userId không được null");
        }
        locale = (locale == null || locale.isBlank()) ? "vi" : locale;
        timezone = timezone == null ? ZoneId.of("Asia/Ho_Chi_Minh") : timezone;
    }
}
