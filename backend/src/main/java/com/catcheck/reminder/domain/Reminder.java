package com.catcheck.reminder.domain;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record Reminder(
        UUID id,
        UUID userId,
        UUID catId,
        ReminderType type,
        ScheduleKind scheduleKind,
        Integer intervalDays,
        String rrule,
        LocalTime preferredTimeStart,
        LocalTime preferredTimeEnd,
        String timezone,
        Instant nextRunAt,
        Instant lastRunAt,
        Instant lastSatisfiedAt,
        List<String> channels,
        ReminderSource source,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static final List<String> SUPPORTED_CHANNELS = List.of("PUSH", "EMAIL", "IN_APP");
}
