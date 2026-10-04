package com.catcheck.notification.api.dto;

import java.util.List;

/** {@code GET /api/v1/notifications} (p8 §8.4.7 G4) — envelope p8 §8.1.4. */
public record NotificationListResponse(List<NotificationView> items, CursorPage page) {
}
