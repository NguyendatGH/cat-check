package com.catcheck.notification.api.dto;

import java.util.List;

public record NotificationListResponse(List<NotificationView> items, CursorPage page) {
}
