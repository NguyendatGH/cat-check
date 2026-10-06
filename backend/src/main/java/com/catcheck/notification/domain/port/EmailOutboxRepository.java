package com.catcheck.notification.domain.port;

import com.catcheck.notification.domain.EmailOutboxMessage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Bảng {@code email_outbox} (p4 F5). */
public interface EmailOutboxRepository {

    /** Va {@code uq_email_outbox_dedupe} ⇒ {@link Optional#empty()}. */
    Optional<UUID> insertIfAbsent(EmailOutboxMessage message);

    /**
     * Lấy việc bằng {@code FOR UPDATE SKIP LOCKED} — lớp bảo vệ thứ hai độc lập với ShedLock
     * (p12 §12.7: dù khoá "thủng" thì hai instance vẫn không xử lý trùng một dòng).
     */
    List<EmailOutboxMessage> claimPending(Instant now, int limit);

    void markSent(UUID id, Instant sentAt);

    /** Thất bại nhưng còn lượt: tăng {@code attempts}, đặt {@code next_attempt_at} theo backoff. */
    void markRetry(UUID id, int attempts, Instant nextAttemptAt, String lastError);

    /** Hết lượt: {@code FAILED} = dead letter, admin gửi lại thủ công (p12 §12.8.1). */
    void markFailed(UUID id, int attempts, String lastError);

    /**
     * L67 — đưa một dòng {@code FAILED} trở lại {@code PENDING} với
     * {@code next_attempt_at = now}, {@code attempts = 0}, {@code last_error = NULL}.
     *
     * <p><b>{@code WHERE status = 'FAILED'} nằm trong chính câu {@code UPDATE}</b>, không phải
     * một lần {@code SELECT} rồi kiểm ở Java: giữa hai câu lệnh đó, {@code SendEmailOutboxJob}
     * (chạy mỗi 30 giây) có thể vừa đổi trạng thái dòng này. Kiểm trong câu lệnh biến cuộc đua
     * đó thành "0 dòng cập nhật" — một kết quả đọc được — thay vì một lần ghi đè im lặng.
     *
     * <p><b>Đặt lại {@code attempts = 0} là có chủ ý.</b> Dòng đã {@code FAILED} nghĩa là đã hết
     * 5 lượt (p12 §12.8.1); giữ nguyên {@code attempts} thì nó sẽ {@code FAILED} lại ngay ở lần
     * thử đầu tiên và nút "gửi lại" của admin thành vô dụng. Người bấm đã thấy lỗi và quyết định
     * thử lại — đó chính là một chu kỳ retry mới.
     *
     * @return {@code true} nếu có đúng một dòng được đưa lại hàng chờ
     */
    boolean requeueFailed(UUID id, Instant now);

    /** Trạng thái hiện tại của một dòng; rỗng nếu không có dòng nào mang id này. */
    Optional<String> findStatus(UUID id);
}
