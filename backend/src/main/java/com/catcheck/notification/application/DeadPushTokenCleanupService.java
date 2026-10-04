package com.catcheck.notification.application;

import com.catcheck.notification.domain.port.PushSubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Phần nghiệp vụ của {@code CleanupDeadPushTokensJob} — p12 §12.3.9 + §12.6.3.
 *
 * <p>Hai thời hạn lấy nguyên từ spec và <b>không</b> là cấu hình: bản ghi đã thu hồi quá
 * <b>30 ngày</b>, hoặc không thấy mặt quá <b>180 ngày</b> (khớp retention D16 ở p15 §15.5.1).
 * p12 §12.6.1 quy tắc 1 ghi rõ con số trong danh mục job là con số duy nhất trong SPEC.</p>
 */
@Service
public class DeadPushTokenCleanupService {

    /** p12 §12.3.9 — xoá bản ghi {@code revoked_at < now() - 30 ngày}. */
    static final Duration REVOKED_RETENTION = Duration.ofDays(30);

    /** p12 §12.3.9 — xoá bản ghi {@code last_seen_at < now() - 180 ngày}. */
    static final Duration STALE_RETENTION = Duration.ofDays(180);

    /** p15 REQ-RET-02 — một lần chạy định xoá quá 20% tổng bản ghi thì dừng và cảnh báo. */
    static final double SAFETY_THRESHOLD_RATIO = 0.20;

    private final PushSubscriptionRepository repository;
    private final Clock clock;

    public DeadPushTokenCleanupService(PushSubscriptionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Kiểm ngưỡng an toàn trước khi xoá bất cứ thứ gì.
     *
     * @return {@code eligible} = số dòng đủ điều kiện, {@code total} = tổng số dòng
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Survey survey(Instant now) {
        return new Survey(
                repository.countDead(now.minus(REVOKED_RETENTION), now.minus(STALE_RETENTION)),
                repository.countAll());
    }

    /** Xoá một lô; mỗi lô một transaction để một lô hỏng không kéo theo lô trước. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deleteBatch(Instant now, int limit) {
        return repository.deleteDead(
                now.minus(REVOKED_RETENTION), now.minus(STALE_RETENTION), limit);
    }

    /**
     * @param eligible số dòng đủ điều kiện xoá tại thời điểm khảo sát
     * @param total    tổng số dòng {@code push_subscription}
     */
    public record Survey(long eligible, long total) {

        /**
         * Chạm ngưỡng an toàn 20% chưa? Bảng rỗng hoặc không có gì để xoá thì không — chia cho
         * 0 và "0 > 0" là hai cách khác nhau để sinh một cảnh báo giả.
         */
        public boolean exceedsSafetyThreshold() {
            return total > 0 && eligible > 0 && (double) eligible / total > SAFETY_THRESHOLD_RATIO;
        }
    }
}
