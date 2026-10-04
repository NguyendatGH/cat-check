package com.catcheck.notification.api.dto;

import java.util.List;

/** {@code GET /api/v1/push/subscriptions} (p8 §8.4.7 G9). Không phân trang (p8 §8.1.4). */
public record PushSubscriptionListResponse(List<PushSubscriptionView> items, CursorPage page) {
}
