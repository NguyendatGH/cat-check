package com.catcheck.notification.application;

import com.catcheck.notification.api.NotificationErrorCode;
import com.catcheck.notification.api.NotificationRequest;
import com.catcheck.notification.application.NotificationTestDoubles.FakeConsentGate;
import com.catcheck.notification.application.NotificationTestDoubles.FakeEmailOutbox;
import com.catcheck.notification.application.NotificationTestDoubles.FakeNotifications;
import com.catcheck.notification.application.NotificationTestDoubles.FakePreferences;
import com.catcheck.notification.application.NotificationTestDoubles.FakePushOutbox;
import com.catcheck.notification.application.NotificationTestDoubles.FakeRecipients;
import com.catcheck.notification.application.NotificationTestDoubles.FakeSubscriptions;
import com.catcheck.notification.domain.AttentionAlertChannel;
import com.catcheck.notification.domain.Notification;
import com.catcheck.notification.domain.NotificationChannel;
import com.catcheck.notification.domain.NotificationRecipient;
import com.catcheck.notification.domain.NotificationStatus;
import com.catcheck.notification.domain.NotificationTemplate;
import com.catcheck.notification.domain.PushPlatform;
import com.catcheck.notification.domain.PushSubscription;
import com.catcheck.notification.domain.QuietHours;
import com.catcheck.notification.domain.UserNotificationPreference;
import com.catcheck.shared.error.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ba luật của {@code NotificationService.enqueue} (p12 §12.1, §12.5.3, §12.9), kiểm bằng cổng
 * trong bộ nhớ — không cần Testcontainers.
 *
 * <p>Múi giờ người nhận cố định là {@code Asia/Ho_Chi_Minh} (UTC+7, không có DST) đúng như mặc
 * định của p12 §12.5.2, nên mốc UTC trong test quy đổi được bằng tay: 15:00Z = 22:00 giờ địa
 * phương = đúng phút bắt đầu giờ im lặng.</p>
 */
class NotificationServiceEnqueueTest {

    private static final ZoneId SAIGON = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final UUID USER = UUID.randomUUID();
    /** 10:00Z = 17:00 giờ VN — ngoài khung im lặng 22:00–07:00. */
    private static final Instant DAYTIME = Instant.parse("2026-10-03T10:00:00Z");
    /** 15:30Z = 22:30 giờ VN — trong khung im lặng. */
    private static final Instant NIGHT = Instant.parse("2026-10-03T15:30:00Z");

    private final FakeNotifications notifications = new FakeNotifications();
    private final FakePushOutbox pushOutbox = new FakePushOutbox();
    private final FakeEmailOutbox emailOutbox = new FakeEmailOutbox();
    private final FakeSubscriptions subscriptions = new FakeSubscriptions();
    private final FakePreferences preferences = new FakePreferences();
    private final FakeConsentGate consentGate = new FakeConsentGate();
    private final FakeRecipients recipients = new FakeRecipients();

    @BeforeEach
    void setUp() {
        recipients.recipient = new NotificationRecipient(USER, "owner@example.com", "vi", SAIGON);
    }

    private NotificationService service(Instant now) {
        return new NotificationService(notifications, pushOutbox, emailOutbox, subscriptions,
                preferences, consentGate, recipients,
                new NotificationProperties(null, null),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private void givenActiveDevice() {
        subscriptions.active.add(new PushSubscription(UUID.randomUUID(), USER, "fid-1", null,
                PushPlatform.WEB, "Chrome", null, DAYTIME, null, null, 0, null, null, DAYTIME));
    }

    private NotificationRequest request(NotificationTemplate template) {
        return new NotificationRequest(USER, template.code(),
                Map.of("deepLink", "/credits"), "Tiêu đề", "Nội dung", "CREDIT", null, "k-1");
    }

    /* ------------------------------------------------------ luật 1: in-app LUÔN được ghi */

    @Test
    void writesInAppRecordEvenWhenPushIsBlocked() {
        givenActiveDevice();
        // KHÔNG cấp consent HEALTH_REMINDER_PUSH.

        service(DAYTIME).enqueue(request(NotificationTemplate.CREDIT_EXPIRING_T48H));

        Optional<Notification> inApp = notifications.channel(NotificationChannel.IN_APP);
        assertThat(inApp).isPresent();
        assertThat(inApp.get().status()).isEqualTo(NotificationStatus.SENT);
        assertThat(inApp.get().sentAt()).isEqualTo(DAYTIME);
    }

    @Test
    void writesInAppRecordAsSuppressedWhenUserTurnedTheTypeOff() {
        preferences.preference = new UserNotificationPreference(
                AttentionAlertChannel.PUSH_AND_INAPP, false, true, false, false, QuietHours.defaults());

        service(DAYTIME).enqueue(request(NotificationTemplate.CREDIT_EXPIRING_T48H));

        Notification inApp = notifications.channel(NotificationChannel.IN_APP).orElseThrow();
        // Vẫn có dòng — p4 F2: để sau này giải thích được "vì sao tôi không nhận được".
        assertThat(inApp.status()).isEqualTo(NotificationStatus.SUPPRESSED);
        assertThat(inApp.payload()).containsEntry(Notification.SUPPRESSED_REASON_KEY, "PREFERENCE_OFF");
    }

    @Test
    void marketingTemplateDoesNotWriteInAppRecord() {
        service(DAYTIME).enqueue(request(NotificationTemplate.MARKETING_PROMOTION));

        assertThat(notifications.channel(NotificationChannel.IN_APP)).isEmpty();
    }

    /* ------------------------------------------- luật 2: preference rồi mới tới consent */

    @Test
    void pushIsSuppressedWithoutHealthReminderPushConsent() {
        givenActiveDevice();

        service(DAYTIME).enqueue(request(NotificationTemplate.CREDIT_EXPIRING_T48H));

        Notification push = notifications.channel(NotificationChannel.PUSH).orElseThrow();
        assertThat(push.status()).isEqualTo(NotificationStatus.SUPPRESSED);
        assertThat(push.payload()).containsEntry(Notification.SUPPRESSED_REASON_KEY, "CONSENT_MISSING");
        assertThat(pushOutbox.inserted).isEmpty();
    }

    @Test
    void pushIsQueuedWhenConsentGrantedAndDeviceRegistered() {
        givenActiveDevice();
        consentGate.granted.add(NotificationTemplate.ConsentPurposes.HEALTH_REMINDER_PUSH);

        service(DAYTIME).enqueue(request(NotificationTemplate.CREDIT_EXPIRING_T48H));

        Notification push = notifications.channel(NotificationChannel.PUSH).orElseThrow();
        assertThat(push.status()).isEqualTo(NotificationStatus.QUEUED);
        assertThat(push.scheduledAt()).isNull();
        assertThat(pushOutbox.inserted).hasSize(1);
        assertThat(pushOutbox.inserted.get(0).nextAttemptAt()).isEqualTo(DAYTIME);
    }

    @Test
    void pushIsSuppressedWhenNoActiveDevice() {
        consentGate.granted.add(NotificationTemplate.ConsentPurposes.HEALTH_REMINDER_PUSH);

        service(DAYTIME).enqueue(request(NotificationTemplate.CREDIT_EXPIRING_T48H));

        assertThat(notifications.channel(NotificationChannel.PUSH).orElseThrow().payload())
                .containsEntry(Notification.SUPPRESSED_REASON_KEY, "NO_ACTIVE_DEVICE");
    }

    @Test
    void mandatoryTemplateStillNeedsConsentForPush() {
        givenActiveDevice();
        // SCAN_RESULT_ATTENTION là "bắt buộc" (p12 §12.2.6) nhưng consent là căn cứ pháp lý,
        // không phải tuỳ chọn trải nghiệm — không được bỏ qua.
        service(DAYTIME).enqueue(request(NotificationTemplate.SCAN_RESULT_ATTENTION));

        assertThat(notifications.channel(NotificationChannel.PUSH).orElseThrow().status())
                .isEqualTo(NotificationStatus.SUPPRESSED);
        assertThat(notifications.channel(NotificationChannel.IN_APP).orElseThrow().status())
                .isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void attentionAlertInAppOnlyLowersChannelButNeverSilencesInApp() {
        givenActiveDevice();
        consentGate.granted.add(NotificationTemplate.ConsentPurposes.HEALTH_REMINDER_PUSH);
        preferences.preference = new UserNotificationPreference(
                AttentionAlertChannel.INAPP_ONLY, true, true, false, false, QuietHours.defaults());

        service(DAYTIME).enqueue(request(NotificationTemplate.SCAN_RESULT_ATTENTION));

        assertThat(notifications.channel(NotificationChannel.PUSH).orElseThrow().payload())
                .containsEntry(Notification.SUPPRESSED_REASON_KEY, "PREFERENCE_OFF");
        assertThat(notifications.channel(NotificationChannel.IN_APP).orElseThrow().status())
                .isEqualTo(NotificationStatus.SENT);
    }

    /* ------------------------------------------------ luật 3: giờ im lặng hoãn, không huỷ */

    @Test
    void quietHoursDefersPushToQuietHoursEnd() {
        givenActiveDevice();
        consentGate.granted.add(NotificationTemplate.ConsentPurposes.HEALTH_REMINDER_PUSH);

        service(NIGHT).enqueue(request(NotificationTemplate.CREDIT_EXPIRING_T48H));

        // 22:30 giờ VN ngày 03/10 ⇒ dời tới 07:00 giờ VN ngày 04/10 = 00:00Z ngày 04/10.
        Instant expected = Instant.parse("2026-10-04T00:00:00Z");
        Notification push = notifications.channel(NotificationChannel.PUSH).orElseThrow();
        assertThat(push.status()).isEqualTo(NotificationStatus.QUEUED);
        assertThat(push.scheduledAt()).isEqualTo(expected);
        assertThat(pushOutbox.inserted.get(0).nextAttemptAt()).isEqualTo(expected);
        // In-app không bị hoãn: nó không phát ra tiếng.
        assertThat(notifications.channel(NotificationChannel.IN_APP).orElseThrow().sentAt())
                .isEqualTo(NIGHT);
    }

    @Test
    void quietHoursDoesNotApplyToTemplatesMarkedTimeCritical() {
        givenActiveDevice();
        consentGate.granted.add(NotificationTemplate.ConsentPurposes.HEALTH_REMINDER_PUSH);

        // T-6h: "còn 6 giờ, dời sang 07:00 là mất credit" (p12 §12.2.6).
        service(NIGHT).enqueue(request(NotificationTemplate.CREDIT_EXPIRING_T6H));

        assertThat(notifications.channel(NotificationChannel.PUSH).orElseThrow().scheduledAt()).isNull();
        assertThat(pushOutbox.inserted.get(0).nextAttemptAt()).isEqualTo(NIGHT);
    }

    @Test
    void quietHoursDisabledSendsImmediately() {
        givenActiveDevice();
        consentGate.granted.add(NotificationTemplate.ConsentPurposes.HEALTH_REMINDER_PUSH);
        preferences.preference = new UserNotificationPreference(
                AttentionAlertChannel.PUSH_AND_INAPP, true, true, false, false,
                new QuietHours(false, LocalTime.of(22, 0), LocalTime.of(7, 0)));

        service(NIGHT).enqueue(request(NotificationTemplate.CREDIT_EXPIRING_T48H));

        assertThat(pushOutbox.inserted.get(0).nextAttemptAt()).isEqualTo(NIGHT);
    }

    /* ----------------------------------------------------------------- email & dedupe key */

    @Test
    void reminderEmailNeedsItsOwnConsentPurpose() {
        consentGate.granted.add(NotificationTemplate.ConsentPurposes.HEALTH_REMINDER_PUSH);

        service(DAYTIME).enqueue(request(NotificationTemplate.REMINDER_SCAN_DUE));

        assertThat(emailOutbox.rows).isEmpty();
        assertThat(notifications.channel(NotificationChannel.EMAIL).orElseThrow().payload())
                .containsEntry(Notification.SUPPRESSED_REASON_KEY, "CONSENT_MISSING");
    }

    @Test
    void dedupeKeyIsSuffixedPerChannelSoThreeRowsCanCoexist() {
        givenActiveDevice();
        consentGate.granted.add(NotificationTemplate.ConsentPurposes.HEALTH_REMINDER_PUSH);
        consentGate.granted.add(NotificationTemplate.ConsentPurposes.HEALTH_REMINDER_EMAIL);

        service(DAYTIME).enqueue(request(NotificationTemplate.REMINDER_SCAN_DUE));

        assertThat(notifications.rows).hasSize(3);
        assertThat(notifications.rows.stream().map(Notification::dedupeKey))
                .containsExactlyInAnyOrder("k-1:IN_APP", "k-1:PUSH", "k-1:EMAIL");
        assertThat(emailOutbox.rows).hasSize(1);
        assertThat(emailOutbox.rows.get(0).dedupeKey()).isEqualTo("k-1:EMAIL");
    }

    @Test
    void unknownTemplateCodeIsRejected() {
        assertThatThrownBy(() -> service(DAYTIME).enqueue(new NotificationRequest(
                USER, "NOT_A_TEMPLATE", Map.of(), null, null, null, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode",
                        NotificationErrorCode.NOTIFICATION_TEMPLATE_UNKNOWN);
    }

    @Test
    void missingRecipientIsSkippedWithoutThrowing() {
        recipients.recipient = null;

        service(DAYTIME).enqueue(request(NotificationTemplate.CREDIT_EXPIRING_T48H));

        assertThat(notifications.rows).isEmpty();
    }
}
