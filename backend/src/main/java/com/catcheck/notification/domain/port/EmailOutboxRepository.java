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
}
