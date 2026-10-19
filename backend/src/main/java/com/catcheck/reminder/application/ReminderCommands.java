package com.catcheck.reminder.application;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class ReminderCommands {

    private ReminderCommands() {
    }

    public record Create(
            UUID userId,
            UUID catId,
            String type,
            String scheduleKind,
            Integer intervalDays,
            String rrule,
            LocalTime preferredTimeStart,
            LocalTime preferredTimeEnd,
            String timezone,
            List<String> channels,
            String source) {
    }

    public record Patch(
            UUID userId,
            UUID reminderId,
            Integer intervalDays,
            String scheduleKind,
            String rrule,
            LocalTime preferredTimeStart,
            LocalTime preferredTimeEnd,
            List<String> channels,
            Boolean active) {
    }
}
