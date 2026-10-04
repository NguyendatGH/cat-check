package com.catcheck.notification.api.dto;

/** {@code GET /api/v1/notifications/unread-count} (p8 §8.4.7 G5) — badge chuông. */
public record UnreadCountResponse(long unreadCount) {
}
