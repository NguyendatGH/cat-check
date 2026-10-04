package com.catcheck.notification.application;

import com.catcheck.notification.domain.NotificationStatus;
import com.catcheck.notification.domain.PushPayload;
import com.catcheck.notification.domain.PushRevokeReason;
import com.catcheck.notification.domain.PushTarget;
import com.catcheck.notification.domain.RetryBackoff;
import com.catcheck.notification.domain.port.NotificationOutboxRepository;
import com.catcheck.notification.domain.port.NotificationOutboxRepository.PendingPush;
import com.catcheck.notification.domain.port.NotificationRepository;
import com.catcheck.notification.domain.port.PushMessageSender;
import com.catcheck.notification.domain.port.PushSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Đẩy {@code notification_outbox} qua FCM — thân của {@code RetryFailedNotificationsJob}
 * (p12 §12.6.3, mỗi 15 phút).
 *
 * <p>Xử lý response theo đúng p12 §12.3.9 / research §5.5:</p>
 * <table>
 *   <tr><th>Kết quả FCM</th><th>Việc phải làm</th></tr>
 *   <tr><td>OK</td><td>{@code status = SENT}, lưu {@code fcm_message_id}, reset
 *       {@code failure_count}</td></tr>
 *   <tr><td>{@code UNREGISTERED} / {@code INVALID_ARGUMENT}</td><td>thu hồi subscription
 *       <b>NGAY</b>, KHÔNG retry — dòng outbox vào {@code FAILED}</td></tr>
 *   <tr><td>{@code UNAVAILABLE} / {@code INTERNAL} / {@code QUOTA_EXCEEDED}</td><td>tăng
 *       {@code failure_count}, backoff; {@code failure_count > 10} ⇒ revoke
 *       {@code STALE}</td></tr>
 * </table>
 *
 * <p><b>Khi chưa cấu hình credential</b> ({@code FIREBASE_SERVICE_ACCOUNT_PATH} rỗng): các dòng
 * đã claim được giữ nguyên {@code PENDING} và <b>không</b> bị tính một lần thử — nếu tính thì
 * mỗi 15 phút một lần, sau 75 phút toàn bộ thông báo push của hệ thống rơi vào dead letter chỉ
 * vì môi trường chưa có khoá.</p>
 */
@Service
public class PushOutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PushOutboxDispatcher.class);

    /** research §5.5: lỗi tạm liên tiếp quá ngưỡng này thì coi là chết. */
    private static final int MAX_CONSECUTIVE_FAILURES = 10;
    private static final int MAX_ERROR_LENGTH = 1000;

    private final NotificationOutboxRepository outbox;
    private final NotificationRepository notifications;
    private final PushSubscriptionRepository subscriptions;
    private final PushMessageSender sender;
    private final NotificationProperties properties;
    private final Clock clock;

    public PushOutboxDispatcher(NotificationOutboxRepository outbox,
                                NotificationRepository notifications,
                                PushSubscriptionRepository subscriptions,
                                PushMessageSender sender,
                                NotificationProperties properties,
                                Clock clock) {
        this.outbox = outbox;
        this.notifications = notifications;
        this.subscriptions = subscriptions;
        this.sender = sender;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public OutboxDispatchReport dispatchPending() {
        Instant now = clock.instant();
        List<PendingPush> batch = outbox.claimPending(now, properties.outbox().pushBatchSize());
        if (batch.isEmpty()) {
            return OutboxDispatchReport.empty();
        }
        if (!sender.enabled()) {
            log.warn("Push chưa được cấu hình (catcheck.notification.push.service-account-path rỗng): "
                    + "{} dòng notification_outbox giữ nguyên PENDING, không tính là một lần thử.",
                    batch.size());
            return OutboxDispatchReport.allSkipped(batch.size());
        }

        int sent = 0;
        int retried = 0;
        int failed = 0;
        int skipped = 0;
        for (Map.Entry<UUID, List<PendingPush>> group : groupByNotification(batch).entrySet()) {
            Counters counters = dispatchOne(group.getKey(), group.getValue(), now);
            sent += counters.sent;
            retried += counters.retried;
            failed += counters.failed;
            skipped += counters.skipped;
        }
        return new OutboxDispatchReport(batch.size(), sent, retried, failed, skipped);
    }

    /** Một multicast cho mỗi thông báo (p12 §12.3.2: KHÔNG loop gửi tuần tự từng thiết bị). */
    private Counters dispatchOne(UUID notificationId, List<PendingPush> entries, Instant now) {
        Counters counters = new Counters();
        PendingPush first = entries.get(0);
        PushPayload payload = payloadOf(first);
        List<PushTarget> targets = entries.stream()
                .map(entry -> new PushTarget(entry.pushSubscriptionId(), entry.installationId(),
                        entry.legacyToken()))
                .toList();
        Map<UUID, PendingPush> bySubscription = new LinkedHashMap<>();
        entries.forEach(entry -> bySubscription.put(entry.pushSubscriptionId(), entry));

        for (PushMessageSender.Result result : sender.send(targets, payload)) {
            PendingPush entry = bySubscription.get(result.subscriptionId());
            if (entry == null) {
                continue;
            }
            applyResult(entry, result, now, counters);
        }
        if (counters.sent > 0) {
            notifications.updateStatus(notificationId, NotificationStatus.SENT.name(), null);
        } else if (counters.failed == entries.size()) {
            notifications.updateStatus(notificationId, NotificationStatus.FAILED.name(),
                    "Tất cả thiết bị đích đều thất bại");
        }
        return counters;
    }

    private void applyResult(PendingPush entry, PushMessageSender.Result result, Instant now,
                             Counters counters) {
        switch (result.status()) {
            case DELIVERED -> {
                outbox.markSent(entry.outboxId(), result.messageId(), now);
                subscriptions.recordSuccess(entry.pushSubscriptionId(), now);
                counters.sent++;
            }
            case PERMANENT_FAILURE -> {
                // Không retry: token/FID đã chết hẳn (p12 §12.3.9).
                subscriptions.revokeById(entry.pushSubscriptionId(), revokeReasonFor(result.errorCode()), now);
                outbox.markFailed(entry.outboxId(), entry.attempts() + 1, result.errorCode(),
                        truncate(result.errorMessage()));
                counters.failed++;
            }
            case TRANSIENT_FAILURE -> {
                int failures = subscriptions.recordFailure(entry.pushSubscriptionId(), now);
                if (failures > MAX_CONSECUTIVE_FAILURES) {
                    subscriptions.revokeById(entry.pushSubscriptionId(), PushRevokeReason.STALE, now);
                }
                int attempts = entry.attempts() + 1;
                if (RetryBackoff.isExhausted(attempts, properties.outbox().maxAttempts())) {
                    outbox.markFailed(entry.outboxId(), attempts, result.errorCode(),
                            truncate(result.errorMessage()));
                    counters.failed++;
                } else {
                    outbox.markRetry(entry.outboxId(), attempts,
                            RetryBackoff.nextAttemptAt(now, attempts), result.errorCode(),
                            truncate(result.errorMessage()));
                    counters.retried++;
                }
            }
            // Giữ nguyên PENDING, không tăng attempts.
            case SKIPPED -> counters.skipped++;
        }
    }

    private PushRevokeReason revokeReasonFor(String errorCode) {
        return "UNREGISTERED".equals(errorCode)
                ? PushRevokeReason.FCM_UNREGISTERED
                : PushRevokeReason.FCM_INVALID;
    }

    private PushPayload payloadOf(PendingPush entry) {
        Object deepLink = entry.payload().get("deepLink");
        Map<String, String> data = new LinkedHashMap<>();
        data.put("templateCode", entry.templateCode());
        data.put("notificationId", entry.notificationId().toString());
        return new PushPayload(entry.title(), entry.body(),
                deepLink == null ? null : String.valueOf(deepLink), data);
    }

    private Map<UUID, List<PendingPush>> groupByNotification(List<PendingPush> batch) {
        Map<UUID, List<PendingPush>> grouped = new LinkedHashMap<>();
        for (PendingPush entry : batch) {
            grouped.computeIfAbsent(entry.notificationId(), key -> new ArrayList<>()).add(entry);
        }
        return grouped;
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > MAX_ERROR_LENGTH ? value.substring(0, MAX_ERROR_LENGTH) : value;
    }

    /** Bộ đếm cục bộ, không phải trạng thái chia sẻ giữa các lượt chạy. */
    private static final class Counters {
        private int sent;
        private int retried;
        private int failed;
        private int skipped;
    }
}
