package com.catcheck.notification.application;

import com.catcheck.notification.domain.EmailOutboxMessage;
import com.catcheck.notification.domain.OutgoingEmail;
import com.catcheck.notification.domain.RetryBackoff;
import com.catcheck.notification.domain.port.EmailOutboxRepository;
import com.catcheck.notification.domain.port.EmailTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Đẩy {@code email_outbox} qua SMTP — thân của {@code SendEmailOutboxJob} (p12 §12.6.3, mỗi 30
 * giây).
 *
 * <p>Lấy việc bằng {@code FOR UPDATE SKIP LOCKED} (p12 §12.6.1 quy tắc 3b) nên dù ShedLock có
 * "thủng" vì job chạy quá {@code lockAtMostFor} thì hai instance vẫn không gửi trùng một dòng.</p>
 *
 * <p><b>Không log nội dung email, địa chỉ nhận hay biến payload</b> — mã OTP nằm trong payload
 * (xem {@code OtpService}) và p17 §17.10b có job CI quét PII trong log. Chỉ log mã template và
 * số lượng.</p>
 */
@Service
public class EmailOutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EmailOutboxDispatcher.class);
    private static final int MAX_ERROR_LENGTH = 1000;

    private final EmailOutboxRepository outbox;
    private final EmailTransport transport;
    private final NotificationProperties properties;
    private final Clock clock;

    public EmailOutboxDispatcher(EmailOutboxRepository outbox,
                                 EmailTransport transport,
                                 NotificationProperties properties,
                                 Clock clock) {
        this.outbox = outbox;
        this.transport = transport;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public OutboxDispatchReport dispatchPending() {
        Instant now = clock.instant();
        List<EmailOutboxMessage> batch = outbox.claimPending(now, properties.outbox().emailBatchSize());
        if (batch.isEmpty()) {
            return OutboxDispatchReport.empty();
        }
        int sent = 0;
        int retried = 0;
        int failed = 0;
        for (EmailOutboxMessage message : batch) {
            try {
                transport.deliver(new OutgoingEmail(message.toAddress(), message.templateCode(),
                        message.locale(), message.payload()));
                outbox.markSent(message.id(), now);
                sent++;
            } catch (RuntimeException ex) {
                int attempts = message.attempts() + 1;
                String reason = describe(ex);
                if (RetryBackoff.isExhausted(attempts, properties.outbox().maxAttempts())) {
                    // Dead letter: admin gửi lại thủ công từ màn p14 (p12 §12.8.1).
                    outbox.markFailed(message.id(), attempts, reason);
                    failed++;
                    log.error("email_outbox vào dead letter sau {} lần thử (template={})",
                            attempts, message.templateCode());
                } else {
                    outbox.markRetry(message.id(), attempts,
                            RetryBackoff.nextAttemptAt(now, attempts), reason);
                    retried++;
                }
            }
        }
        return new OutboxDispatchReport(batch.size(), sent, retried, failed, 0);
    }

    /**
     * Chỉ giữ loại lỗi + thông điệp, cắt ngắn. Ghi vào {@code email_outbox.last_error} (cùng
     * bảng đã chứa {@code to_address}) — KHÔNG đưa ra log.
     */
    private String describe(RuntimeException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        String combined = ex.getClass().getSimpleName() + ": " + message;
        return combined.length() > MAX_ERROR_LENGTH ? combined.substring(0, MAX_ERROR_LENGTH) : combined;
    }
}
