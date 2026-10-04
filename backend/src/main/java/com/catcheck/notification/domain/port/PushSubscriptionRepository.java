package com.catcheck.notification.domain.port;

import com.catcheck.notification.domain.PushRevokeReason;
import com.catcheck.notification.domain.PushSubscription;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Bảng {@code push_subscription} (p4 F3). */
public interface PushSubscriptionRepository {

    /** G9 — thiết bị đang nhận push của tôi (chỉ dòng chưa thu hồi). */
    List<PushSubscription> findActiveByUser(UUID userId);

    long countActiveByUser(UUID userId);

    Optional<PushSubscription> findActiveByDeviceKey(UUID userId, String deviceKey);

    Optional<PushSubscription> findByIdForUser(UUID subscriptionId, UUID userId);

    /**
     * G10 — upsert theo {@code (user_id, COALESCE(fid, legacy_token))}. Dòng đã thu hồi của
     * CHÍNH user này được kích hoạt lại (reset {@code failure_count}).
     */
    PushSubscription upsert(PushSubscription subscription);

    /**
     * Hai người dùng chung máy: một FID chỉ thuộc MỘT user đang hoạt động (partial unique
     * {@code uq_push_subscription_fid_active}). Phải thu hồi đăng ký của user cũ TRƯỚC khi
     * upsert cho user mới, nếu không người sau nhận push của người trước (p4 F3).
     */
    int revokeInstallationIdOwnedByOtherUser(String installationId, UUID newOwnerId, Instant now);

    boolean revoke(UUID subscriptionId, UUID userId, PushRevokeReason reason, Instant now);

    void revokeById(UUID subscriptionId, PushRevokeReason reason, Instant now);

    void recordSuccess(UUID subscriptionId, Instant now);

    /** Tăng {@code failure_count}; trả về giá trị sau khi tăng để job quyết định có revoke không. */
    int recordFailure(UUID subscriptionId, Instant now);

    /* --------------------------------------------------- CleanupDeadPushTokensJob (p12 §12.3.9) */

    /** Tổng số dòng — mẫu số của ngưỡng an toàn 20% (p12 §12.6.1 quy tắc 6, p15 REQ-RET-02). */
    long countAll();

    /**
     * Số dòng đủ điều kiện xoá: {@code revoked_at < revokedBefore} <b>HOẶC</b>
     * {@code last_seen_at < lastSeenBefore} (p12 §12.3.9, retention D16 ở p15 §15.5.1).
     */
    long countDead(Instant revokedBefore, Instant lastSeenBefore);

    /**
     * Xoá tối đa {@code limit} dòng đủ điều kiện; trả về số dòng đã xoá.
     *
     * <p>Đây là {@code DELETE} thật chứ không phải soft delete — p12 §12.3.9 nói "xoá bản ghi".
     * {@code notification_outbox} trỏ tới đây bằng FK {@code ON DELETE CASCADE} (V13) nên các
     * dòng giao vận đi kèm biến mất cùng; {@code notification} không trỏ tới
     * {@code push_subscription} nên lịch sử thông báo in-app vẫn nguyên.</p>
     */
    int deleteDead(Instant revokedBefore, Instant lastSeenBefore, int limit);
}

