package com.catcheck.notification.application;

import com.catcheck.notification.api.NotificationErrorCode;
import com.catcheck.notification.api.NotificationGateway;
import com.catcheck.notification.api.NotificationRequest;
import com.catcheck.notification.api.SystemBroadcastGateway;
import com.catcheck.notification.application.spi.NotificationRecipientPort;
import com.catcheck.notification.domain.NotificationTemplate;
import com.catcheck.shared.error.BusinessRuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SystemBroadcastService implements SystemBroadcastGateway {

    private static final Logger log = LoggerFactory.getLogger(SystemBroadcastService.class);

    private static final int RECIPIENT_PAGE_SIZE = 500;

    private final NotificationGateway notifications;
    private final NotificationRecipientPort recipients;
    private final Clock clock;

    public SystemBroadcastService(NotificationGateway notifications,
                                  NotificationRecipientPort recipients,
                                  Clock clock) {
        this.notifications = notifications;
        this.recipients = recipients;
        this.clock = clock;
    }

    @Override
    public BroadcastResult broadcast(String templateCode, String title, String body,
                                     Map<String, Object> payload, boolean dryRun) {
        if (!ALLOWED_TEMPLATE_CODES.contains(templateCode)) {
            throw new BusinessRuleException(
                    NotificationErrorCode.NOTIFICATION_TEMPLATE_UNKNOWN, templateCode);
        }
        NotificationTemplate template = NotificationTemplate.fromCode(templateCode)
                .orElseThrow(() -> new BusinessRuleException(
                        NotificationErrorCode.NOTIFICATION_TEMPLATE_UNKNOWN, templateCode));

        Instant startedAt = clock.instant();
        String dedupePrefix = template.code() + ":BROADCAST:" + startedAt.getEpochSecond() / 60;

        int matched = 0;
        int queued = 0;
        UUID cursor = null;
        while (true) {
            List<UUID> page = recipients.findActiveRecipientIdsAfter(cursor, RECIPIENT_PAGE_SIZE);
            if (page.isEmpty()) {
                break;
            }
            matched += page.size();
            if (!dryRun) {
                for (UUID userId : page) {
                    notifications.enqueue(new NotificationRequest(
                            userId, template.code(), payload, title, body,
                            null, null, dedupePrefix + ':' + userId));
                    queued++;
                }
            }
            cursor = page.getLast();
            if (page.size() < RECIPIENT_PAGE_SIZE) {
                break;
            }
        }

        log.info("Broadcast {} dryRun={} matched={} queued={}",
                template.code(), dryRun, matched, queued);
        return new BroadcastResult(matched, queued, dedupePrefix);
    }
}
