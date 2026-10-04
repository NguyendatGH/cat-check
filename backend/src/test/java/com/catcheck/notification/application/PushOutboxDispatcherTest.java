package com.catcheck.notification.application;

import com.catcheck.notification.application.NotificationTestDoubles.FakeNotifications;
import com.catcheck.notification.application.NotificationTestDoubles.FakePushOutbox;
import com.catcheck.notification.application.NotificationTestDoubles.FakePushSender;
import com.catcheck.notification.application.NotificationTestDoubles.FakeSubscriptions;
import com.catcheck.notification.domain.PushRevokeReason;
import com.catcheck.notification.domain.port.NotificationOutboxRepository.PendingPush;
import com.catcheck.notification.domain.port.PushMessageSender;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Xử lý response FCM của {@code RetryFailedNotificationsJob} (p12 §12.3.9, §12.8.1).
 *
 * <p>Ba nhánh phải tách bạch: thành công, lỗi <b>vĩnh viễn</b> (thu hồi ngay, không retry), lỗi
 * <b>tạm</b> (backoff). Cộng với nhánh thứ tư mà p12 không nói tới nhưng vận hành bắt buộc
 * phải có: chưa cấu hình credential.</p>
 */
class PushOutboxDispatcherTest {

    private static final Instant NOW = Instant.parse("2026-10-03T10:00:00Z");

    private final FakePushOutbox outbox = new FakePushOutbox();
    private final FakeNotifications notifications = new FakeNotifications();
    private final FakeSubscriptions subscriptions = new FakeSubscriptions();
    private final FakePushSender sender = new FakePushSender();

    private PushOutboxDispatcher dispatcher() {
        return new PushOutboxDispatcher(outbox, notifications, subscriptions, sender,
                new NotificationProperties(null, null), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private UUID givenPending(int attempts) {
        UUID subscriptionId = UUID.randomUUID();
        outbox.pending.add(new PendingPush(UUID.randomUUID(), UUID.randomUUID(), subscriptionId,
                attempts, "fid-1", null, "CREDIT_EXPIRING_T48H", "Tiêu đề", "Nội dung",
                Map.of("deepLink", "/credits")));
        return subscriptionId;
    }

    @Test
    void successMarksOutboxSentAndResetsFailureCount() {
        UUID subscriptionId = givenPending(0);

        OutboxDispatchReport report = dispatcher().dispatchPending();

        assertThat(report.sent()).isEqualTo(1);
        assertThat(outbox.sent).hasSize(1);
        assertThat(subscriptions.successes).contains(subscriptionId);
    }

    @Test
    void unregisteredRevokesTheSubscriptionImmediatelyWithoutRetry() {
        UUID subscriptionId = givenPending(0);
        sender.scripted.put(subscriptionId, new PushMessageSender.Result(subscriptionId,
                PushMessageSender.Status.PERMANENT_FAILURE, null, "UNREGISTERED", "gone"));

        OutboxDispatchReport report = dispatcher().dispatchPending();

        assertThat(report.failed()).isEqualTo(1);
        assertThat(outbox.retried).isEmpty();
        assertThat(subscriptions.revoked).containsValue(PushRevokeReason.FCM_UNREGISTERED);
    }

    @Test
    void invalidArgumentRevokesWithTheOtherReasonCode() {
        UUID subscriptionId = givenPending(0);
        sender.scripted.put(subscriptionId, new PushMessageSender.Result(subscriptionId,
                PushMessageSender.Status.PERMANENT_FAILURE, null, "INVALID_ARGUMENT", "bad"));

        dispatcher().dispatchPending();

        assertThat(subscriptions.revoked).containsValue(PushRevokeReason.FCM_INVALID);
    }

    /** Lỗi tạm lần đầu ⇒ chờ 1 phút (mốc đầu của p12 §12.8.1). */
    @Test
    void transientFailureSchedulesTheFirstBackoffStep() {
        UUID subscriptionId = givenPending(0);
        sender.scripted.put(subscriptionId, new PushMessageSender.Result(subscriptionId,
                PushMessageSender.Status.TRANSIENT_FAILURE, null, "UNAVAILABLE", "try later"));

        OutboxDispatchReport report = dispatcher().dispatchPending();

        assertThat(report.retried()).isEqualTo(1);
        assertThat(outbox.retried.values()).containsExactly(Instant.parse("2026-10-03T10:01:00Z"));
        assertThat(outbox.retryAttempts.values()).containsExactly(1);
        assertThat(subscriptions.revoked).isEmpty();
    }

    /** Lần thất bại thứ 4 ⇒ chờ 2 giờ. */
    @Test
    void transientFailureUsesTheFourthBackoffStepAfterThreeFailures() {
        UUID subscriptionId = givenPending(3);
        sender.scripted.put(subscriptionId, new PushMessageSender.Result(subscriptionId,
                PushMessageSender.Status.TRANSIENT_FAILURE, null, "INTERNAL", "boom"));

        dispatcher().dispatchPending();

        assertThat(outbox.retried.values()).containsExactly(Instant.parse("2026-10-03T12:00:00Z"));
        assertThat(outbox.retryAttempts.values()).containsExactly(4);
    }

    /** Lần thứ 5 là hết lượt ⇒ dead letter, KHÔNG xếp lịch nữa. */
    @Test
    void fifthTransientFailureGoesToDeadLetter() {
        UUID subscriptionId = givenPending(4);
        sender.scripted.put(subscriptionId, new PushMessageSender.Result(subscriptionId,
                PushMessageSender.Status.TRANSIENT_FAILURE, null, "UNAVAILABLE", "still down"));

        OutboxDispatchReport report = dispatcher().dispatchPending();

        assertThat(report.failed()).isEqualTo(1);
        assertThat(outbox.retried).isEmpty();
        assertThat(outbox.failed).containsValue("UNAVAILABLE");
    }

    /** Quá 10 lần lỗi tạm liên tiếp ⇒ coi là chết (research §5.5). */
    @Test
    void elevenConsecutiveTransientFailuresRevokeTheSubscriptionAsStale() {
        UUID subscriptionId = givenPending(0);
        subscriptions.failureCounts.put(subscriptionId, 10);
        sender.scripted.put(subscriptionId, new PushMessageSender.Result(subscriptionId,
                PushMessageSender.Status.TRANSIENT_FAILURE, null, "UNAVAILABLE", "down"));

        dispatcher().dispatchPending();

        assertThat(subscriptions.revoked).containsEntry(subscriptionId, PushRevokeReason.STALE);
    }

    /** Thiếu credential: giữ nguyên PENDING, KHÔNG tiêu attempts. */
    @Test
    void disabledSenderLeavesEverythingPending() {
        givenPending(0);
        sender.enabled = false;

        OutboxDispatchReport report = dispatcher().dispatchPending();

        assertThat(report.skipped()).isEqualTo(1);
        assertThat(outbox.sent).isEmpty();
        assertThat(outbox.retried).isEmpty();
        assertThat(outbox.failed).isEmpty();
    }

    @Test
    void emptyBatchDoesNothing() {
        assertThat(dispatcher().dispatchPending()).isEqualTo(OutboxDispatchReport.empty());
    }
}
