package com.catcheck.credit.infrastructure.notification;

import com.catcheck.credit.application.spi.CreditExpiryNotificationPort;
import com.catcheck.credit.domain.ExpiryReminderMilestone;
import com.catcheck.notification.api.NotificationGateway;
import com.catcheck.notification.api.NotificationRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
class CreditExpiryNotificationAdapter implements CreditExpiryNotificationPort {

    private static final String REF_TYPE_CREDIT = "CREDIT";

    private static final String DEEP_LINK = "/credits";

    private final NotificationGateway notifications;

    CreditExpiryNotificationAdapter(NotificationGateway notifications) {
        this.notifications = notifications;
    }

    @Override
    public void queueExpiryReminder(
            UUID userId,
            UUID batchId,
            ExpiryReminderMilestone milestone,
            Instant expiresAt,
            int remainingAmount) {

        String templateCode = milestone.templateCode();
        notifications.enqueue(new NotificationRequest(
                userId,
                templateCode,
                Map.of(
                        "batchId", batchId.toString(),
                        "remaining", remainingAmount,
                        "expiresAt", expiresAt.toString(),
                        "milestone", milestone.name(),
                        "deepLink", DEEP_LINK),
                title(milestone),
                body(milestone, remainingAmount),
                REF_TYPE_CREDIT,
                batchId,
                templateCode + ":CREDIT:" + batchId + ':' + milestone.name()));
    }

    private static String title(ExpiryReminderMilestone milestone) {
        return milestone == ExpiryReminderMilestone.T48H
                ? "Credit sắp hết hạn"
                : "Credit sắp hết hạn — còn 6 giờ";
    }

    private static String body(ExpiryReminderMilestone milestone, int remainingAmount) {
        return milestone == ExpiryReminderMilestone.T48H
                ? "Bạn còn " + remainingAmount + " credit sắp hết hạn trong 2 ngày. "
                        + "Dùng ngay để không bị mất."
                : "Chỉ còn 6 giờ trước khi " + remainingAmount + " credit hết hạn.";
    }
}
