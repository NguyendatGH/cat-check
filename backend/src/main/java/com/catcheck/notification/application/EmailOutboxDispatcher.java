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

    private String describe(RuntimeException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        String combined = ex.getClass().getSimpleName() + ": " + message;
        return combined.length() > MAX_ERROR_LENGTH ? combined.substring(0, MAX_ERROR_LENGTH) : combined;
    }
}
