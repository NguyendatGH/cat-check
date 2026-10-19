package com.catcheck.notification.domain;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public record QuietHours(boolean enabled, LocalTime start, LocalTime end) {

    public QuietHours {
        if (start == null || end == null) {
            throw new IllegalArgumentException("quietHours.start/end không được null");
        }
    }

    public static QuietHours defaults() {
        return new QuietHours(true, LocalTime.of(22, 0), LocalTime.of(7, 0));
    }

    public boolean isQuietAt(Instant moment, ZoneId zone) {
        if (!enabled || start.equals(end)) {
            return false;
        }
        LocalTime localTime = ZonedDateTime.ofInstant(moment, zone).toLocalTime();
        if (start.isBefore(end)) {
            return !localTime.isBefore(start) && localTime.isBefore(end);
        }
        return !localTime.isBefore(start) || localTime.isBefore(end);
    }

    public Instant nextAllowedAfter(Instant moment, ZoneId zone) {
        if (!isQuietAt(moment, zone)) {
            return moment;
        }
        ZonedDateTime local = ZonedDateTime.ofInstant(moment, zone);
        ZonedDateTime candidate = local.toLocalDate().atTime(end).atZone(zone);
        if (!candidate.toInstant().isAfter(moment)) {
            candidate = candidate.plusDays(1);
        }
        return candidate.toInstant();
    }
}
