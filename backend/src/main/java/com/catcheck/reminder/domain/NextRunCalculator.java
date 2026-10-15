package com.catcheck.reminder.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class NextRunCalculator {

    private NextRunCalculator() {
    }

    public static Instant nextRun(Reminder reminder, Instant from) {
        if (reminder.scheduleKind() == ScheduleKind.RRULE) {
            if (reminder.rrule() == null || reminder.rrule().isBlank()) {
                return null;
            }
            return RecurrenceRule.parse(reminder.rrule()).nextAfter(
                    from, reminder.createdAt(), ZoneId.of(reminder.timezone()), reminder.preferredTimeStart());
        }
        if (reminder.scheduleKind() != ScheduleKind.INTERVAL || reminder.intervalDays() == null) {
            return null;
        }
        ZoneId zone = ZoneId.of(reminder.timezone());
        LocalTime at = reminder.preferredTimeStart() != null ? reminder.preferredTimeStart() : LocalTime.of(9, 0);
        LocalDate day = from.atZone(zone).toLocalDate().plusDays(reminder.intervalDays());
        ZonedDateTime candidate = day.atTime(at).atZone(zone);
        ZonedDateTime now = from.atZone(zone);
        while (!candidate.toInstant().isAfter(now.toInstant())) {
            candidate = candidate.plusDays(reminder.intervalDays());
        }
        return candidate.toInstant();
    }
}
