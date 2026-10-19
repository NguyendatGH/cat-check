package com.catcheck.reminder.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ActiveReminderQuery {

    List<ActiveReminder> activeRemindersFor(UUID userId, UUID catId);

    record ActiveReminder(String type, Instant nextRunAt) {
    }
}
