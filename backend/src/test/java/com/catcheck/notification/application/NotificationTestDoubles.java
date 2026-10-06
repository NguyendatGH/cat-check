package com.catcheck.notification.application;

import com.catcheck.notification.application.spi.ConsentGatePort;
import com.catcheck.notification.application.spi.NotificationRecipientPort;
import com.catcheck.notification.domain.EmailOutboxMessage;
import com.catcheck.notification.domain.Notification;
import com.catcheck.notification.domain.NotificationChannel;
import com.catcheck.notification.domain.NotificationRecipient;
import com.catcheck.notification.domain.PushPayload;
import com.catcheck.notification.domain.PushRevokeReason;
import com.catcheck.notification.domain.PushSubscription;
import com.catcheck.notification.domain.PushTarget;
import com.catcheck.notification.domain.UserNotificationPreference;
import com.catcheck.notification.domain.port.EmailOutboxRepository;
import com.catcheck.notification.domain.port.NotificationOutboxRepository;
import com.catcheck.notification.domain.port.NotificationPreferenceRepository;
import com.catcheck.notification.domain.port.NotificationRepository;
import com.catcheck.notification.domain.port.PushMessageSender;
import com.catcheck.notification.domain.port.PushSubscriptionRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Cổng trong bộ nhớ cho test của module notification — kiểm chứng luật phát thông báo
 * (p12 §12.1, §12.5.3, §12.9) mà không cần Testcontainers.
 */
final class NotificationTestDoubles {

    private NotificationTestDoubles() {
    }

    /** Bảng {@code notification} trong bộ nhớ, giữ nguyên ngữ nghĩa UNIQUE của {@code dedupe_key}. */
    static final class FakeNotifications implements NotificationRepository {

        final List<Notification> rows = new ArrayList<>();
        private final Set<String> dedupeKeys = new HashSet<>();
        final Map<UUID, String> statusUpdates = new LinkedHashMap<>();

        @Override
        public Optional<Notification> insertIfAbsent(Notification notification) {
            if (notification.dedupeKey() != null && !dedupeKeys.add(notification.dedupeKey())) {
                return Optional.empty();
            }
            Notification stored = new Notification(
                    UUID.randomUUID(), notification.userId(), notification.channel(),
                    notification.template(), notification.payload(), notification.titleSnapshot(),
                    notification.bodySnapshot(), notification.status(), notification.refType(),
                    notification.refId(), notification.dedupeKey(), notification.attemptCount(),
                    notification.scheduledAt(), notification.sentAt(), notification.readAt(),
                    notification.createdAt());
            rows.add(stored);
            return Optional.of(stored);
        }

        @Override
        public Page listInbox(UUID userId, String cursor, int limit) {
            List<Notification> items = rows.stream()
                    .filter(row -> row.userId().equals(userId))
                    .filter(row -> row.channel() == NotificationChannel.IN_APP)
                    .toList();
            return new Page(items, null, false);
        }

        @Override
        public long countUnread(UUID userId) {
            return rows.stream()
                    .filter(row -> row.userId().equals(userId) && row.channel() == NotificationChannel.IN_APP)
                    .filter(Notification::unread)
                    .count();
        }

        @Override
        public Optional<Notification> findByIdForUser(UUID notificationId, UUID userId) {
            return rows.stream()
                    .filter(row -> row.id().equals(notificationId) && row.userId().equals(userId))
                    .findFirst();
        }

        @Override
        public boolean markRead(UUID notificationId, UUID userId) {
            return true;
        }

        @Override
        public int markAllRead(UUID userId) {
            return 0;
        }

        @Override
        public boolean hide(UUID notificationId, UUID userId) {
            return true;
        }

        @Override
        public void updateStatus(UUID notificationId, String status, String lastError) {
            statusUpdates.put(notificationId, status);
        }

        Optional<Notification> channel(NotificationChannel channel) {
            return rows.stream().filter(row -> row.channel() == channel).findFirst();
        }
    }

    /** Bảng {@code notification_outbox} trong bộ nhớ. */
    static final class FakePushOutbox implements NotificationOutboxRepository {

        record Row(UUID notificationId, UUID subscriptionId, Instant nextAttemptAt) {
        }

        final List<Row> inserted = new ArrayList<>();
        final List<PendingPush> pending = new ArrayList<>();
        final Map<UUID, String> sent = new LinkedHashMap<>();
        final Map<UUID, Instant> retried = new LinkedHashMap<>();
        final Map<UUID, Integer> retryAttempts = new LinkedHashMap<>();
        final Map<UUID, String> failed = new LinkedHashMap<>();

        @Override
        public void insertIfAbsent(UUID notificationId, UUID pushSubscriptionId, Instant nextAttemptAt) {
            inserted.add(new Row(notificationId, pushSubscriptionId, nextAttemptAt));
        }

        @Override
        public List<PendingPush> claimPending(Instant now, int limit) {
            return List.copyOf(pending);
        }

        @Override
        public void markSent(UUID id, String fcmMessageId, Instant sentAt) {
            sent.put(id, fcmMessageId);
        }

        @Override
        public void markRetry(UUID id, int attempts, Instant nextAttemptAt, String errorCode, String lastError) {
            retried.put(id, nextAttemptAt);
            retryAttempts.put(id, attempts);
        }

        @Override
        public void markFailed(UUID id, int attempts, String errorCode, String lastError) {
            failed.put(id, errorCode);
        }

        /* --------------------------------------------------- L67 (gui lai FAILED) */

        /** Dong duoc dat {@code FAILED} ⇒ {@code requeueFailed} thanh cong dung mot lan. */
        @Override
        public boolean requeueFailed(UUID id, Instant now) {
            if (!failed.containsKey(id)) {
                return false;
            }
            failed.remove(id);
            requeued.put(id, now);
            return true;
        }

        @Override
        public Optional<String> findStatus(UUID id) {
            if (failed.containsKey(id)) {
                return Optional.of("FAILED");
            }
            if (requeued.containsKey(id) || retried.containsKey(id)) {
                return Optional.of("PENDING");
            }
            if (sent.containsKey(id)) {
                return Optional.of("SENT");
            }
            return Optional.empty();
        }

        final Map<UUID, Instant> requeued = new LinkedHashMap<>();
    }

    /** Bảng {@code email_outbox} trong bộ nhớ. */
    static final class FakeEmailOutbox implements EmailOutboxRepository {

        final List<EmailOutboxMessage> rows = new ArrayList<>();

        @Override
        public Optional<UUID> insertIfAbsent(EmailOutboxMessage message) {
            rows.add(message);
            return Optional.of(UUID.randomUUID());
        }

        @Override
        public List<EmailOutboxMessage> claimPending(Instant now, int limit) {
            return List.copyOf(rows);
        }

        @Override
        public void markSent(UUID id, Instant sentAt) {
        }

        @Override
        public void markRetry(UUID id, int attempts, Instant nextAttemptAt, String lastError) {
        }

        @Override
        public void markFailed(UUID id, int attempts, String lastError) {
            failed.add(id);
        }

        /* --------------------------------------------------- L67 (gui lai FAILED) */

        final Set<UUID> failed = new HashSet<>();
        final Map<UUID, Instant> requeued = new LinkedHashMap<>();

        @Override
        public boolean requeueFailed(UUID id, Instant now) {
            if (!failed.remove(id)) {
                return false;
            }
            requeued.put(id, now);
            return true;
        }

        @Override
        public Optional<String> findStatus(UUID id) {
            if (failed.contains(id)) {
                return Optional.of("FAILED");
            }
            return requeued.containsKey(id) ? Optional.of("PENDING") : Optional.empty();
        }
    }

    /** Bảng {@code push_subscription} trong bộ nhớ. */
    static final class FakeSubscriptions implements PushSubscriptionRepository {

        final List<PushSubscription> active = new ArrayList<>();
        final Map<UUID, PushRevokeReason> revoked = new LinkedHashMap<>();
        final Map<UUID, Integer> failureCounts = new HashMap<>();
        final Set<UUID> successes = new HashSet<>();

        @Override
        public List<PushSubscription> findActiveByUser(UUID userId) {
            return active.stream().filter(row -> row.userId().equals(userId)).toList();
        }

        @Override
        public long countActiveByUser(UUID userId) {
            return findActiveByUser(userId).size();
        }

        @Override
        public Optional<PushSubscription> findActiveByDeviceKey(UUID userId, String deviceKey) {
            return active.stream()
                    .filter(row -> row.userId().equals(userId) && deviceKey.equals(row.deviceKey()))
                    .findFirst();
        }

        @Override
        public Optional<PushSubscription> findByIdForUser(UUID subscriptionId, UUID userId) {
            return active.stream()
                    .filter(row -> row.id().equals(subscriptionId) && row.userId().equals(userId))
                    .findFirst();
        }

        @Override
        public PushSubscription upsert(PushSubscription subscription) {
            active.add(subscription);
            return subscription;
        }

        @Override
        public int revokeInstallationIdOwnedByOtherUser(String installationId, UUID newOwnerId, Instant now) {
            return 0;
        }

        @Override
        public boolean revoke(UUID subscriptionId, UUID userId, PushRevokeReason reason, Instant now) {
            revoked.put(subscriptionId, reason);
            return true;
        }

        @Override
        public void revokeById(UUID subscriptionId, PushRevokeReason reason, Instant now) {
            revoked.put(subscriptionId, reason);
        }

        @Override
        public void recordSuccess(UUID subscriptionId, Instant now) {
            successes.add(subscriptionId);
        }

        @Override
        public int recordFailure(UUID subscriptionId, Instant now) {
            return failureCounts.merge(subscriptionId, 1, Integer::sum);
        }

        /* ------------------------------------- CleanupDeadPushTokensJob (p12 §12.3.9) */

        /** Dòng đã bị {@link #deleteDead} xoá — fake không mô phỏng điều kiện thời gian. */
        final List<UUID> deleted = new ArrayList<>();

        /** Số dòng "đủ điều kiện xoá" mà fake này báo cáo; test đặt tay. */
        long deadCount;

        /** Tổng số dòng mà fake này báo cáo — mẫu số của ngưỡng an toàn 20%. */
        long totalCount;

        @Override
        public long countAll() {
            return totalCount;
        }

        @Override
        public long countDead(Instant revokedBefore, Instant lastSeenBefore) {
            return deadCount;
        }

        @Override
        public int deleteDead(Instant revokedBefore, Instant lastSeenBefore, int limit) {
            int toDelete = (int) Math.min(limit, deadCount - deleted.size());
            for (int i = 0; i < toDelete; i++) {
                deleted.add(UUID.randomUUID());
            }
            return Math.max(0, toDelete);
        }
    }

    /** Tuỳ chọn thông báo cố định. */
    static final class FakePreferences implements NotificationPreferenceRepository {

        UserNotificationPreference preference = UserNotificationPreference.defaults();

        @Override
        public UserNotificationPreference findByUserIdOrDefaults(UUID userId) {
            return preference;
        }
    }

    /** Consent gate: mặc định CHƯA đồng ý gì cả — đúng tinh thần p15 §15.3.1 C4. */
    static final class FakeConsentGate implements ConsentGatePort {

        final Set<String> granted = new HashSet<>();

        @Override
        public boolean isGranted(UUID userId, String purposeCode) {
            return granted.contains(purposeCode);
        }
    }

    /** Người nhận cố định. */
    static final class FakeRecipients implements NotificationRecipientPort {

        NotificationRecipient recipient;

        /** L72 — danh sách id để fan-out; để rỗng thì broadcast không có ai nhận. */
        final List<UUID> broadcastIds = new ArrayList<>();

        @Override
        public Optional<NotificationRecipient> findById(UUID userId) {
            return Optional.ofNullable(recipient);
        }

        @Override
        public List<UUID> findActiveRecipientIdsAfter(UUID afterId, int limit) {
            return broadcastIds.stream()
                    .filter(id -> afterId == null || id.compareTo(afterId) > 0)
                    .limit(limit)
                    .toList();
        }
    }

    /** {@link PushMessageSender} trả kết quả dựng sẵn cho từng subscription. */
    static final class FakePushSender implements PushMessageSender {

        boolean enabled = true;
        final Map<UUID, Result> scripted = new LinkedHashMap<>();

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public List<Result> send(List<PushTarget> targets, PushPayload payload) {
            return targets.stream()
                    .map(target -> scripted.getOrDefault(target.subscriptionId(),
                            Result.delivered(target.subscriptionId(), "msg-" + target.subscriptionId())))
                    .toList();
        }
    }
}
