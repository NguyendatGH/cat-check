package com.catcheck.notification.domain.port;

import com.catcheck.notification.domain.EmailOutboxMessage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmailOutboxRepository {

    Optional<UUID> insertIfAbsent(EmailOutboxMessage message);

    List<EmailOutboxMessage> claimPending(Instant now, int limit);

    void markSent(UUID id, Instant sentAt);

    void markRetry(UUID id, int attempts, Instant nextAttemptAt, String lastError);

    void markFailed(UUID id, int attempts, String lastError);

    boolean requeueFailed(UUID id, Instant now);

    Optional<String> findStatus(UUID id);
}
