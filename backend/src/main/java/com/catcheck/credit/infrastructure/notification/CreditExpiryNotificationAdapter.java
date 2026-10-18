package com.catcheck.credit.infrastructure.notification;

import com.catcheck.credit.application.spi.CreditExpiryNotificationPort;
import com.catcheck.credit.domain.ExpiryReminderMilestone;
import com.catcheck.notification.api.NotificationGateway;
import com.catcheck.notification.api.NotificationRequest;
import com.catcheck.shared.i18n.MessageResolver;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
class CreditExpiryNotificationAdapter implements CreditExpiryNotificationPort {

    private static final String REF_TYPE_CREDIT = "CREDIT";

    private static final String DEEP_LINK = "/credits";

    static final String KEY_T48H = "notif.credit.expiring_48h";

    static final String KEY_T6H = "notif.credit.expiring_6h";

    private final NotificationGateway notifications;
    private final MessageResolver messages;

    CreditExpiryNotificationAdapter(NotificationGateway notifications, MessageResolver messages) {
        this.notifications = notifications;
        this.messages = messages;
    }

    @Override
    public void queueExpiryReminder(
            UUID userId,
            UUID batchId,
            ExpiryReminderMilestone milestone,
            Instant expiresAt,
            int remainingAmount) {

        String templateCode = milestone.templateCode();
        String messageKey = milestone == ExpiryReminderMilestone.T48H ? KEY_T48H : KEY_T6H;
        Locale locale = notifications.recipientLocale(userId);
        notifications.enqueue(new NotificationRequest(
                userId,
                templateCode,
                Map.of(
                        "batchId", batchId.toString(),
                        "remaining", remainingAmount,
                        "expiresAt", expiresAt.toString(),
                        "milestone", milestone.name(),
                        "deepLink", DEEP_LINK),
                messages.resolve(messageKey + ".title", locale),
                messages.resolve(messageKey + ".body", new Object[]{remainingAmount}, locale),
                REF_TYPE_CREDIT,
                batchId,
                templateCode + ":CREDIT:" + batchId + ':' + milestone.name()));
    }

}
