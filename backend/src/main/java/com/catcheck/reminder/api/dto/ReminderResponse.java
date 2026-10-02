package com.catcheck.reminder.api.dto;

import com.catcheck.reminder.domain.Reminder;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

/** Một lịch nhắc trả về client (p8 I1/I2/I3/I4). DTO là record — ArchUnit R14. */
public record ReminderResponse(
        String id,
        String catId,
        String type,
        String scheduleKind,
        Integer intervalDays,
        String rrule,
        LocalTime preferredTimeStart,
        LocalTime preferredTimeEnd,
        String timezone,
        Instant nextRunAt,
        Instant lastRunAt,
        Instant lastSatisfiedAt,
        List<String> channels,
        String source,
        boolean active) {

    public static ReminderResponse from(Reminder r) {
        return new ReminderResponse(
                r.id().toString(),
                r.catId() == null ? null : r.catId().toString(),
                r.type().name(),
                r.scheduleKind().name(),
                r.intervalDays(),
                r.rrule(),
                r.preferredTimeStart(),
                r.preferredTimeEnd(),
                r.timezone(),
                r.nextRunAt(),
                r.lastRunAt(),
                r.lastSatisfiedAt(),
                r.channels(),
                r.source().name(),
                r.active());
    }
}
