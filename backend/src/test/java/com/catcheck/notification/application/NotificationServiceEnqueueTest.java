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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationServiceEnqueueTest {

    private static final ZoneId SAIGON = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final UUID USER = UUID.randomUUID();
    private static final Instant DAYTIME = Instant.parse("2026-10-03T10:00:00Z");
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

    @Test
    void recipientLocaleIsTheStoredLocaleAndDefaultsToVietnamese() {
        recipients.recipient = new NotificationRecipient(USER, "owner@example.com", "en", SAIGON);
        assertThat(service(DAYTIME).recipientLocale(USER)).isEqualTo(Locale.ENGLISH);

        recipients.recipient = null;
        assertThat(service(DAYTIME).recipientLocale(USER).getLanguage()).isEqualTo("vi");
    }

    @Test
    void writesInAppRecordEvenWhenPushIsBlocked() {
        givenActiveDevice();

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
        assertThat(inApp.status()).isEqualTo(NotificationStatus.SUPPRESSED);
        assertThat(inApp.payload()).containsEntry(Notification.SUPPRESSED_REASON_KEY, "PREFERENCE_OFF");
    }

    @Test
    void marketingTemplateDoesNotWriteInAppRecord() {
        service(DAYTIME).enqueue(request(NotificationTemplate.MARKETING_PROMOTION));

        assertThat(notifications.channel(NotificationChannel.IN_APP)).isEmpty();
    }


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


    @Test
    void quietHoursDefersPushToQuietHoursEnd() {
        givenActiveDevice();
        consentGate.granted.add(NotificationTemplate.ConsentPurposes.HEALTH_REMINDER_PUSH);

        service(NIGHT).enqueue(request(NotificationTemplate.CREDIT_EXPIRING_T48H));

        Instant expected = Instant.parse("2026-10-04T00:00:00Z");
        Notification push = notifications.channel(NotificationChannel.PUSH).orElseThrow();
        assertThat(push.status()).isEqualTo(NotificationStatus.QUEUED);
        assertThat(push.scheduledAt()).isEqualTo(expected);
        assertThat(pushOutbox.inserted.get(0).nextAttemptAt()).isEqualTo(expected);
        assertThat(notifications.channel(NotificationChannel.IN_APP).orElseThrow().sentAt())
                .isEqualTo(NIGHT);
    }

    @Test
    void quietHoursDoesNotApplyToTemplatesMarkedTimeCritical() {
        givenActiveDevice();
        consentGate.granted.add(NotificationTemplate.ConsentPurposes.HEALTH_REMINDER_PUSH);

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
    void enqueueReportsWhetherAnythingWasActuallyInserted() {
        NotificationService service = service(DAYTIME);

        assertThat(service.enqueue(request(NotificationTemplate.CREDIT_EXPIRING_T48H))).isTrue();
        // Cùng dedupe_key lần hai: mọi kênh va uq_notification_dedupe ⇒ không ghi gì mới.
        assertThat(service.enqueue(request(NotificationTemplate.CREDIT_EXPIRING_T48H))).isFalse();
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

        assertThat(service(DAYTIME).enqueue(request(NotificationTemplate.CREDIT_EXPIRING_T48H))).isFalse();

        assertThat(notifications.rows).isEmpty();
    }
}
