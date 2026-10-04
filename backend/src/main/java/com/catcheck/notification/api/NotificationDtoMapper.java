package com.catcheck.notification.api;

import com.catcheck.notification.api.dto.NotificationView;
import com.catcheck.notification.api.dto.PushSubscriptionView;
import com.catcheck.notification.domain.Notification;
import com.catcheck.notification.domain.PushSubscription;

/** Map miền → DTO. Viết tay (research §11: không dùng MapStruct cho DTO dạng record). */
final class NotificationDtoMapper {

    private NotificationDtoMapper() {
    }

    static NotificationView toView(Notification notification) {
        Object deepLink = notification.payload().get("deepLink");
        return new NotificationView(
                notification.id(),
                notification.template().code(),
                notification.titleSnapshot(),
                notification.bodySnapshot(),
                deepLink == null ? null : String.valueOf(deepLink),
                notification.refType() == null ? null : notification.refType().name(),
                notification.refId(),
                !notification.unread(),
                notification.readAt(),
                notification.createdAt());
    }

    static PushSubscriptionView toView(PushSubscription subscription) {
        return new PushSubscriptionView(
                subscription.id(),
                subscription.platform().name(),
                subscription.deviceLabel(),
                subscription.lastSeenAt(),
                subscription.lastSuccessAt(),
                subscription.createdAt());
    }
}
