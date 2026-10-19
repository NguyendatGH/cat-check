package com.catcheck.notification.application;

import com.catcheck.notification.api.OutboxAdminGateway;
import com.catcheck.notification.domain.port.EmailOutboxRepository;
import com.catcheck.notification.domain.port.NotificationOutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class OutboxAdminService implements OutboxAdminGateway {

    private final EmailOutboxRepository emailOutbox;
    private final NotificationOutboxRepository pushOutbox;
    private final Clock clock;

    public OutboxAdminService(EmailOutboxRepository emailOutbox,
                              NotificationOutboxRepository pushOutbox,
                              Clock clock) {
        this.emailOutbox = emailOutbox;
        this.pushOutbox = pushOutbox;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ResendOutcome resendFailed(UUID outboxId) {
        Instant now = clock.instant();
        if (emailOutbox.requeueFailed(outboxId, now) || pushOutbox.requeueFailed(outboxId, now)) {
            return ResendOutcome.REQUEUED;
        }
        Optional<String> status = emailOutbox.findStatus(outboxId)
                .or(() -> pushOutbox.findStatus(outboxId));
        return status.isPresent() ? ResendOutcome.NOT_FAILED : ResendOutcome.NOT_FOUND;
    }
}
