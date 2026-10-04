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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Trung tâm thông báo: phát thông báo (p12 §12.1–12.2) và phục vụ hộp thư in-app (p8 nhóm G,
 * G4–G8).
 *
 * <p><b>Ba quy tắc của {@link #enqueue} lấy thẳng từ p12, theo đúng thứ tự áp dụng:</b></p>
 * <ol>
 *   <li><b>In-app luôn được ghi</b> cho mọi template không-marketing (§12.1): "mọi thông báo
 *       quan trọng phải LUÔN có bản ghi in-app, bất kể user có bật push/email hay không — đây
 *       là nguồn sự thật". Khi user đã tắt loại thông báo đó thì dòng vẫn được ghi nhưng ở
 *       trạng thái {@code SUPPRESSED}, để về sau giải thích được "vì sao tôi không nhận được"
 *       (p4 F2).</li>
 *   <li><b>Preference rồi mới tới consent.</b> Hai lớp khác bản chất: preference
 *       ({@code user_notification_preference}) là tuỳ chọn trải nghiệm và ĐƯỢC mặc định bật;
 *       consent ({@code consent_record}) là căn cứ pháp lý và TUYỆT ĐỐI không được mặc định bật
 *       (p12 §12.9.2, p15 §15.10-C). Template "bắt buộc" (§12.2.6) bỏ qua được preference nhưng
 *       <b>không</b> bỏ qua consent.</li>
 *   <li><b>Giờ im lặng hoãn, không huỷ</b> (§12.5.3): push/email của template có áp dụng quiet
 *       hours được đặt {@code scheduled_at}/{@code next_attempt_at} = {@code quiet_hours_end},
 *       in-app thì không bị ảnh hưởng (in-app không phát ra tiếng).
 *       <br><b>Chỗ p4 và p12 đọc khác nhau:</b> p4 F2 liệt kê giờ im lặng là một trong hai
 *       nguyên nhân của {@code SUPPRESSED}, còn p12 §12.5.3 nói rõ "không gửi ngay, dời sang
 *       {@code quiet_hours_end}". Đã chọn cách của p12 vì 02-team-decisions §0 giao miền "job
 *       nền, scheduler, thông báo" cho p12 (p4 sở hữu tên bảng/tên cột); huỷ hẳn một cảnh báo
 *       credit chỉ vì nó rơi vào 23:00 là mất thông tin, không phải tiết chế.</li>
 * </ol>
 *
 * <p>Toàn bộ phương thức chỉ {@code INSERT}; không gọi SMTP/FCM (p12 §12.4, §12.8).</p>
 */
@Service
public class NotificationService implements NotificationGateway {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /** Giá trị ghi vào {@code payload.suppressedReason}. */
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
    public void enqueue(NotificationRequest request) {
        NotificationTemplate template = NotificationTemplate.fromCode(request.templateCode())
                .orElseThrow(() -> new BusinessRuleException(
                        NotificationErrorCode.NOTIFICATION_TEMPLATE_UNKNOWN, request.templateCode()));

        Optional<NotificationRecipient> found = recipients.findById(request.userId());
        if (found.isEmpty()) {
            // Tài khoản đã bị xoá/ẩn danh giữa lúc sự kiện nghiệp vụ xảy ra và lúc phát thông
            // báo. Không ném: không được làm rollback transaction nghiệp vụ vì chuyện này.
            log.warn("Bỏ qua thông báo {}: không còn tài khoản tương ứng.", template.code());
            return;
        }
        NotificationRecipient recipient = found.get();
        UserNotificationPreference preference = preferences.findByUserIdOrDefaults(request.userId());
        Instant now = clock.instant();
        PreferenceGate gate = template.preferenceGate();
        boolean typeEnabled = template.mandatory() || preference.allows(gate);

        if (template.writesInAppRecord()) {
            writeInApp(request, template, typeEnabled, now);
        }
        if (template.supportsPush()) {
            enqueuePush(request, template, preference, recipient, typeEnabled, now);
        }
        if (template.supportsEmail()) {
            enqueueEmail(request, template, preference, recipient, typeEnabled, now);
        }
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

    /**
     * Rút consent {@code HEALTH_REMINDER_PUSH} ⇒ thu hồi mọi thiết bị (p12 §12.3.9, H15.74a).
     *
     * <p>Hiện thực nằm ở đây chứ không uỷ quyền sang {@code PushSubscriptionService}: cổng
     * {@code NotificationGateway} là bề mặt duy nhất module khác được chạm, và việc thu hồi
     * chỉ là một {@code UPDATE} theo user — kéo thêm một service vào chỉ để gọi đúng một
     * phương thức repository sẽ tạo thêm một cạnh phụ thuộc trong module mà không đổi hành
     * vi.</p>
     */
    @Override
    @Transactional
    public int revokePushOnConsentWithdrawal(UUID userId) {
        Instant now = clock.instant();
        List<PushSubscription> active = subscriptions.findActiveByUser(userId);
        active.forEach(subscription ->
                subscriptions.revokeById(subscription.id(), PushRevokeReason.CONSENT_WITHDRAWN, now));
        if (!active.isEmpty()) {
            // KHÔNG log fid/legacy_token (p11 §11.10.3: token là PII) — chỉ số lượng.
            log.info("Rut consent HEALTH_REMINDER_PUSH: da thu hoi {} dang ky push.", active.size());
        }
        return active.size();
    }

    /* ------------------------------------------------------------------ kênh in-app */

    private void writeInApp(NotificationRequest request, NotificationTemplate template,
                            boolean typeEnabled, Instant now) {
        NotificationStatus status = typeEnabled ? NotificationStatus.SENT : NotificationStatus.SUPPRESSED;
        Map<String, Object> payload = typeEnabled
                ? request.payload()
                : withReason(request.payload(), REASON_PREFERENCE_OFF);
        notifications.insertIfAbsent(row(request, template, NotificationChannel.IN_APP, status, payload,
                null, typeEnabled ? now : null, now));
    }

    /* -------------------------------------------------------------------- kênh push */

    private void enqueuePush(NotificationRequest request, NotificationTemplate template,
                             UserNotificationPreference preference, NotificationRecipient recipient,
                             boolean typeEnabled, Instant now) {
        PreferenceGate gate = template.preferenceGate();
        // Cảnh báo "cần chú ý" không tắt được, chỉ hạ kênh về in-app — p12 §12.9.1. Vì vậy
        // template bắt buộc vẫn phải tôn trọng attention_alert_channel.
        boolean pushAllowedByPreference = gate == PreferenceGate.ATTENTION_CHANNEL
                ? preference.allowsPushFor(gate)
                : typeEnabled;
        if (!pushAllowedByPreference) {
            suppress(request, template, NotificationChannel.PUSH, REASON_PREFERENCE_OFF, now);
            return;
        }
        boolean consented = template.pushConsentPurpose()
                .map(purpose -> consentGate.isGranted(request.userId(), purpose))
                .orElse(false);
        if (!consented) {
            suppress(request, template, NotificationChannel.PUSH, REASON_CONSENT_MISSING, now);
            return;
        }
        List<PushSubscription> targets = subscriptions.findActiveByUser(request.userId()).stream()
                .limit(properties.push().maxDevicesPerUser())
                .toList();
        if (targets.isEmpty()) {
            suppress(request, template, NotificationChannel.PUSH, REASON_NO_ACTIVE_DEVICE, now);
            return;
        }
        Instant deliverAt = deliveryTime(template, preference, recipient, now);
        Optional<Notification> created = notifications.insertIfAbsent(
                row(request, template, NotificationChannel.PUSH, NotificationStatus.QUEUED,
                        request.payload(), deliverAt.equals(now) ? null : deliverAt, null, now));
        if (created.isEmpty()) {
            // Va dedupe_key: thông báo này đã được phát trước đó, không fan-out lần hai.
            return;
        }
        UUID notificationId = created.get().id();
        for (PushSubscription target : targets) {
            pushOutbox.insertIfAbsent(notificationId, target.id(), deliverAt);
        }
    }

    /* ------------------------------------------------------------------- kênh email */

    private void enqueueEmail(NotificationRequest request, NotificationTemplate template,
                              UserNotificationPreference preference, NotificationRecipient recipient,
                              boolean typeEnabled, Instant now) {
        if (!typeEnabled) {
            suppress(request, template, NotificationChannel.EMAIL, REASON_PREFERENCE_OFF, now);
            return;
        }
        Optional<String> purpose = template.emailConsentPurpose();
        if (purpose.isPresent() && !consentGate.isGranted(request.userId(), purpose.get())) {
            suppress(request, template, NotificationChannel.EMAIL, REASON_CONSENT_MISSING, now);
            return;
        }
        Instant deliverAt = deliveryTime(template, preference, recipient, now);
        Optional<Notification> created = notifications.insertIfAbsent(
                row(request, template, NotificationChannel.EMAIL, NotificationStatus.QUEUED,
                        request.payload(), deliverAt.equals(now) ? null : deliverAt, null, now));
        if (created.isEmpty()) {
            return;
        }
        emailOutbox.insertIfAbsent(new EmailOutboxMessage(
                null, recipient.emailAddress(), template.code(), recipient.locale(),
                request.payload(), OutboxStatus.PENDING, 0, deliverAt, null,
                channelDedupeKey(request.dedupeKey(), NotificationChannel.EMAIL), null, null));
    }

    /* ----------------------------------------------------------- hộp thư in-app (G4–G8) */

    /** G4 — {@code GET /notifications}. */
    @Transactional(readOnly = true)
    public NotificationRepository.Page listInbox(UUID userId, String cursor, int limit) {
        if (limit < 1 || limit > 100) {
            // p8 §8.1.4: vượt trần ⇒ 400, KHÔNG âm thầm kẹp xuống (kẹp làm client tưởng đã lấy hết).
            throw new BusinessRuleException(NotificationErrorCode.VALIDATION_FAILED, "limit");
        }
        return notifications.listInbox(userId, cursor, limit);
    }

    /** G5 — {@code GET /notifications/unread-count}. */
    @Transactional(readOnly = true)
    public long countUnread(UUID userId) {
        return notifications.countUnread(userId);
    }

    /** G6 — {@code POST /notifications/{id}/read}. Idempotent: đọc lại vẫn {@code 204}. */
    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        requireOwned(userId, notificationId);
        notifications.markRead(notificationId, userId);
    }

    /** G7 — {@code POST /notifications/read-all}. */
    @Transactional
    public int markAllRead(UUID userId) {
        return notifications.markAllRead(userId);
    }

    /** G8 — {@code DELETE /notifications/{id}}: ẩn khỏi hộp thư (soft). */
    @Transactional
    public void hide(UUID userId, UUID notificationId) {
        requireOwned(userId, notificationId);
        notifications.hide(notificationId, userId);
    }

    private void requireOwned(UUID userId, UUID notificationId) {
        notifications.findByIdForUser(notificationId, userId)
                .orElseThrow(() -> new NotFoundException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
    }

    /* ----------------------------------------------------------------------- helper */

    /**
     * Giờ im lặng tính theo {@code app_user.timezone} (p12 §12.5.2), trả về mốc UTC sớm nhất
     * được phép đẩy.
     */
    private Instant deliveryTime(NotificationTemplate template, UserNotificationPreference preference,
                                 NotificationRecipient recipient, Instant now) {
        if (!template.quietHoursApply()) {
            return now;
        }
        return preference.quietHours().nextAllowedAfter(now, recipient.timezone());
    }

    private void suppress(NotificationRequest request, NotificationTemplate template,
                          NotificationChannel channel, String reason, Instant now) {
        notifications.insertIfAbsent(row(request, template, channel, NotificationStatus.SUPPRESSED,
                withReason(request.payload(), reason), null, null, now));
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

    /**
     * {@code uq_notification_dedupe} là UNIQUE trên TOÀN bảng, nhưng một thông báo nghiệp vụ
     * sinh tới ba dòng (in-app + push + email). Nên khoá phải mang hậu tố kênh, nếu không dòng
     * thứ hai bị chính dòng thứ nhất chặn và push/email không bao giờ được xếp hàng.
     */
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
