package com.catcheck.notification.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Backoff của outbox: <b>1 phút / 5 phút / 30 phút / 2 giờ / 12 giờ</b>, tối đa <b>5 lần thử</b>
 * rồi chuyển {@code FAILED} = dead letter (p12 §12.4 + §12.8.1, khớp research §8).
 *
 * <p><b>Điểm p12 tự mâu thuẫn, đã chọn cách đọc và ghi lại ở đây:</b> "tối đa 5 lần thử" cộng
 * với "5 mốc backoff" không khớp nhau — nếu lần thử thứ 5 thất bại là hết thì mốc 12 giờ không
 * bao giờ dùng tới. Cách đọc đã chọn: {@code attempts} đếm số lần ĐÃ GỬI; sau lần thất bại thứ
 * {@code n} thì chờ {@code DELAYS[n-1]}; {@code n >= maxAttempts} ⇒ {@code FAILED}. Với
 * {@code maxAttempts = 5} (mặc định) thì mốc 12 giờ chỉ được dùng khi vận hành nâng
 * {@code catcheck.notification.outbox.max-attempts} lên 6 — giữ nguyên bảng 5 mốc để không
 * phải sửa code khi đổi cấu hình.</p>
 */
public final class RetryBackoff {

    /** Mặc định p12 §12.8.1. */
    public static final int DEFAULT_MAX_ATTEMPTS = 5;

    private static final List<Duration> DELAYS = List.of(
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(30),
            Duration.ofHours(2),
            Duration.ofHours(12));

    private RetryBackoff() {
    }

    /**
     * Khoảng chờ sau lần thất bại thứ {@code attempt} (1-based). Vượt bảng thì giữ nguyên mốc
     * cuối (12 giờ) thay vì ném lỗi — job không được chết vì một dòng dữ liệu lạ.
     */
    public static Duration delayAfterFailure(int attempt) {
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt phải >= 1, nhận " + attempt);
        }
        return DELAYS.get(Math.min(attempt, DELAYS.size()) - 1);
    }

    /** Mốc thử lại kế tiếp sau lần thất bại thứ {@code attempt}. */
    public static Instant nextAttemptAt(Instant now, int attempt) {
        return now.plus(delayAfterFailure(attempt));
    }

    /** Đã hết lượt ⇒ chuyển {@code FAILED} (dead letter, admin gửi lại thủ công). */
    public static boolean isExhausted(int attempts, int maxAttempts) {
        return attempts >= maxAttempts;
    }

    /** Bảng mốc backoff, để test đối chiếu đúng thứ tự p12. */
    public static List<Duration> delays() {
        return DELAYS;
    }
}
