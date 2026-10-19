package com.catcheck.reminder.domain.port;

import com.catcheck.reminder.domain.Reminder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReminderRepository {

    Reminder insert(Reminder reminder);

    Optional<Reminder> findByIdForUser(UUID id, UUID userId);

    List<Reminder> findAllForUser(UUID userId, UUID catIdFilter, Boolean activeFilter);

    Reminder update(Reminder reminder);

    void softDelete(UUID id, UUID userId);

    Optional<UUID> findActiveIdByCatAndType(UUID userId, UUID catId, String type);


    List<UUID> findDueIds(Instant now, int limit);

    Optional<Reminder> lockDue(UUID id, Instant now);

    void markDispatched(UUID id, Instant sentAt, Instant nextRunAt);

    /** Active RRULE reminders still without {@code next_run_at}, keyset by id. */
    List<Reminder> findUnscheduledRrule(UUID afterId, int limit);

    /** Sets {@code next_run_at} only while it is still NULL; returns whether a row changed. */
    boolean scheduleIfUnscheduled(UUID id, Instant nextRunAt);
}
