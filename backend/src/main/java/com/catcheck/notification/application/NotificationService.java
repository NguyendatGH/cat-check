package com.catcheck.notification.application;

import com.catcheck.notification.api.NotificationErrorCode;
import com.catcheck.notification.api.NotificationGateway;
import com.catcheck.notification.api.NotificationRequest;
import com.catcheck.notification.api.TransactionalEmailRequest;
import com.catcheck.notification.application.spi.ConsentGatePort;
import com.catcheck.notification.application.spi.NotificationRecipientPort;
import com.catcheck.notification.domain.EmailOutboxMessage;
import com.catcheck.notification.domain.Notification;
import com.catcheck.notification.domain.NotificationChannel;
import com.catcheck.notification.domain.NotificationRecipient;
import com.catcheck.notification.domain.NotificationRefType;
import com.catcheck.notification.domain.NotificationStatus;
import com.catcheck.notification.domain.NotificationTemplate;
import com.catcheck.notification.domain.OutboxStatus;
import com.catcheck.notification.domain.PreferenceGate;
import com.catcheck.notification.domain.PushRevokeReason;
import com.catcheck.notification.domain.PushSubscription;
import com.catcheck.notification.domain.UserNotificationPreference;
import com.catcheck.notification.domain.port.EmailOutboxRepository;
import com.catcheck.notification.domain.port.NotificationOutboxRepository;
import com.catcheck.notification.domain.port.NotificationPreferenceRepository;
import com.catcheck.notification.domain.port.NotificationRepository;
import com.catcheck.notification.domain.port.PushSubscriptionRepository;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationService implements NotificationGateway {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    static final String REASON_PREFERENCE_OFF = "PREFERENCE_OFF";
    static final String REASON_CONSENT_MISSING = "CONSENT_MISSING";
    static final String REASON_NO_ACTIVE_DEVICE = "NO_ACTIVE_DEVICE";

    private final NotificationRepository notifications;
    private final NotificationOutboxRepository pushOutbox;
    private final EmailOutboxRepository emailOutbox;
    private final PushSubscriptionRepository subscriptions;
    private final NotificationPreferenceRepository preferences;
    private final ConsentGatePort consentGate;
    private final NotificationRecipientPort recipients;
    private final NotificationProperties properties;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications,
                               NotificationOutboxRepository pushOutbox,
                               EmailOutboxRepository emailOutbox,
                               PushSubscriptionRepository subscriptions,
                               NotificationPreferenceRepository preferences,
                               ConsentGatePort consentGate,
                               NotificationRecipientPort recipients,
                               NotificationProperties properties,
                               Clock clock) {
        this.notifications = notifications;
        this.pushOutbox = pushOutbox;
        this.emailOutbox = emailOutbox;
        this.subscriptions = subscriptions;
        this.preferences = preferences;
        this.consentGate = consentGate;
        this.recipients = recipients;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public boolean enqueue(NotificationRequest request) {
        NotificationTemplate template = NotificationTemplate.fromCode(request.templateCode())
                .orElseThrow(() -> new BusinessRuleException(
                        NotificationErrorCode.NOTIFICATION_TEMPLATE_UNKNOWN, request.templateCode()));

        Optional<NotificationRecipient> found = recipients.findById(request.userId());
        if (found.isEmpty()) {
            log.warn("Bỏ qua thông báo {}: không còn tài khoản tương ứng.", template.code());
            return false;
        }
        NotificationRecipient recipient = found.get();
        UserNotificationPreference preference = preferences.findByUserIdOrDefaults(request.userId());
        Instant now = clock.instant();
        PreferenceGate gate = template.preferenceGate();
        boolean typeEnabled = template.mandatory() || preference.allows(gate);

        boolean inserted = false;
        if (template.writesInAppRecord()) {
            inserted |= writeInApp(request, template, typeEnabled, now);
        }
        if (template.supportsPush()) {
            inserted |= enqueuePush(request, template, preference, recipient, typeEnabled, now);
        }
        if (template.supportsEmail()) {
            inserted |= enqueueEmail(request, template, preference, recipient, typeEnabled, now);
        }
        return inserted;
    }

    @Override
    @Transactional
    public void enqueueTransactionalEmail(TransactionalEmailRequest request) {
        NotificationTemplate template = NotificationTemplate.fromCode(request.templateCode())
                .orElseThrow(() -> new BusinessRuleException(
                        NotificationErrorCode.NOTIFICATION_TEMPLATE_UNKNOWN, request.templateCode()));
        emailOutbox.insertIfAbsent(new EmailOutboxMessage(
                null, request.toAddress(), template.code(), request.locale(), request.payload(),
                OutboxStatus.PENDING, 0, clock.instant(), null, request.dedupeKey(), null, null));
    }

    @Override
    public Locale recipientLocale(UUID userId) {
        return Locale.forLanguageTag(recipients.findById(userId).map(NotificationRecipient::locale).orElse("vi"));
    }

    @Override
    @Transactional
    public int revokePushOnConsentWithdrawal(UUID userId) {
        Instant now = clock.instant();
        List<PushSubscription> active = subscriptions.findActiveByUser(userId);
        active.forEach(subscription ->
                subscriptions.revokeById(subscription.id(), PushRevokeReason.CONSENT_WITHDRAWN, now));
        if (!active.isEmpty()) {
            log.info("Rut consent HEALTH_REMINDER_PUSH: da thu hoi {} dang ky push.", active.size());
        }
        return active.size();
    }


    private boolean writeInApp(NotificationRequest request, NotificationTemplate template,
                               boolean typeEnabled, Instant now) {
        NotificationStatus status = typeEnabled ? NotificationStatus.SENT : NotificationStatus.SUPPRESSED;
        Map<String, Object> payload = typeEnabled
                ? request.payload()
                : withReason(request.payload(), REASON_PREFERENCE_OFF);
        return notifications.insertIfAbsent(row(request, template, NotificationChannel.IN_APP, status, payload,
                null, typeEnabled ? now : null, now)).isPresent();
    }


    private boolean enqueuePush(NotificationRequest request, NotificationTemplate template,
                                UserNotificationPreference preference, NotificationRecipient recipient,
                             boolean typeEnabled, Instant now) {
        PreferenceGate gate = template.preferenceGate();
        boolean pushAllowedByPreference = gate == PreferenceGate.ATTENTION_CHANNEL
                ? preference.allowsPushFor(gate)
                : typeEnabled;
        if (!pushAllowedByPreference) {
            return suppress(request, template, NotificationChannel.PUSH, REASON_PREFERENCE_OFF, now);
        }
        boolean consented = template.pushConsentPurpose()
                .map(purpose -> consentGate.isGranted(request.userId(), purpose))
                .orElse(false);
        if (!consented) {
            return suppress(request, template, NotificationChannel.PUSH, REASON_CONSENT_MISSING, now);
        }
        List<PushSubscription> targets = subscriptions.findActiveByUser(request.userId()).stream()
                .limit(properties.push().maxDevicesPerUser())
                .toList();
        if (targets.isEmpty()) {
            return suppress(request, template, NotificationChannel.PUSH, REASON_NO_ACTIVE_DEVICE, now);
        }
        Instant deliverAt = deliveryTime(template, preference, recipient, now);
        Optional<Notification> created = notifications.insertIfAbsent(
                row(request, template, NotificationChannel.PUSH, NotificationStatus.QUEUED,
                        request.payload(), deliverAt.equals(now) ? null : deliverAt, null, now));
        if (created.isEmpty()) {
            return false;
        }
        UUID notificationId = created.get().id();
        for (PushSubscription target : targets) {
            pushOutbox.insertIfAbsent(notificationId, target.id(), deliverAt);
        }
        return true;
    }


    private boolean enqueueEmail(NotificationRequest request, NotificationTemplate template,
                                 UserNotificationPreference preference, NotificationRecipient recipient,
                              boolean typeEnabled, Instant now) {
        if (!typeEnabled) {
            return suppress(request, template, NotificationChannel.EMAIL, REASON_PREFERENCE_OFF, now);
        }
        Optional<String> purpose = template.emailConsentPurpose();
        if (purpose.isPresent() && !consentGate.isGranted(request.userId(), purpose.get())) {
            return suppress(request, template, NotificationChannel.EMAIL, REASON_CONSENT_MISSING, now);
        }
        Instant deliverAt = deliveryTime(template, preference, recipient, now);
        Optional<Notification> created = notifications.insertIfAbsent(
                row(request, template, NotificationChannel.EMAIL, NotificationStatus.QUEUED,
                        request.payload(), deliverAt.equals(now) ? null : deliverAt, null, now));
        if (created.isEmpty()) {
            return false;
        }
        emailOutbox.insertIfAbsent(new EmailOutboxMessage(
                null, recipient.emailAddress(), template.code(), recipient.locale(),
                request.payload(), OutboxStatus.PENDING, 0, deliverAt, null,
                channelDedupeKey(request.dedupeKey(), NotificationChannel.EMAIL), null, null));
        return true;
    }


    @Transactional(readOnly = true)
    public NotificationRepository.Page listInbox(UUID userId, String cursor, int limit) {
        if (limit < 1 || limit > 100) {
            throw new BusinessRuleException(NotificationErrorCode.VALIDATION_FAILED, "limit");
        }
        return notifications.listInbox(userId, parseCursor(cursor), limit);
    }

    private static NotificationRepository.InboxCursor parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return NotificationRepository.InboxCursor.decode(cursor);
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException(NotificationErrorCode.PAGINATION_CURSOR_INVALID);
        }
    }

    @Transactional(readOnly = true)
    public long countUnread(UUID userId) {
        return notifications.countUnread(userId);
    }

    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        requireOwned(userId, notificationId);
        notifications.markRead(notificationId, userId);
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return notifications.markAllRead(userId);
    }

    @Transactional
    public void hide(UUID userId, UUID notificationId) {
        requireOwned(userId, notificationId);
        notifications.hide(notificationId, userId);
    }

    private void requireOwned(UUID userId, UUID notificationId) {
        notifications.findByIdForUser(notificationId, userId)
                .orElseThrow(() -> new NotFoundException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
    }


    private Instant deliveryTime(NotificationTemplate template, UserNotificationPreference preference,
                                 NotificationRecipient recipient, Instant now) {
        if (!template.quietHoursApply()) {
            return now;
        }
        return preference.quietHours().nextAllowedAfter(now, recipient.timezone());
    }

    private boolean suppress(NotificationRequest request, NotificationTemplate template,
                             NotificationChannel channel, String reason, Instant now) {
        return notifications.insertIfAbsent(row(request, template, channel, NotificationStatus.SUPPRESSED,
                withReason(request.payload(), reason), null, null, now)).isPresent();
    }

    private Notification row(NotificationRequest request, NotificationTemplate template,
                             NotificationChannel channel, NotificationStatus status,
                             Map<String, Object> payload, Instant scheduledAt, Instant sentAt,
                             Instant now) {
        return new Notification(
                null,
                request.userId(),
                channel,
                template,
                payload,
                request.title(),
                request.body(),
                status,
                refType(request.refType()),
                request.refId(),
                channelDedupeKey(request.dedupeKey(), channel),
                0,
                scheduledAt,
                sentAt,
                null,
                now);
    }

    private String channelDedupeKey(String base, NotificationChannel channel) {
        return base == null ? null : base + ':' + channel.name();
    }

    private NotificationRefType refType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (NotificationRefType candidate : NotificationRefType.values()) {
            if (candidate.name().equals(raw)) {
                return candidate;
            }
        }
        throw new BusinessRuleException(NotificationErrorCode.VALIDATION_FAILED, "refType");
    }

    private Map<String, Object> withReason(Map<String, Object> payload, String reason) {
        Map<String, Object> merged = new LinkedHashMap<>(payload);
        merged.put(Notification.SUPPRESSED_REASON_KEY, reason);
        return merged;
    }
}
