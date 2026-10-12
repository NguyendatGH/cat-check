package com.catcheck.privacy.domain;

import java.time.Instant;
import java.time.LocalDate;

public record HolidayCalendarEntry(
        LocalDate holidayDate,
        String countryCode,
        String nameVy,
        HolidaySource source,
        Instant createdAt
) {

    public HolidayCalendarEntry {
        if (holidayDate == null || countryCode == null || nameVy == null || source == null) {
            throw new IllegalArgumentException("holidayCalendarEntry thiếu trường bắt buộc");
        }
    }
}
