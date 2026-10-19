package com.catcheck.notification.api.dto;

import java.util.List;

public record PushSubscriptionListResponse(List<PushSubscriptionView> items, CursorPage page) {
}
