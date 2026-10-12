package com.catcheck.privacy.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;

public final class BusinessDays {

    private static final ZoneOffset UTC = ZoneOffset.UTC;

    private BusinessDays() {
    }

    public static Instant addBusinessDays(Instant from, int days, Set<LocalDate> officialHolidays, Instant today) {
        if (days <= 0) {
            throw new IllegalArgumentException("Số ngày làm việc phải > 0: " + days);
        }
        if (officialHolidays == null || officialHolidays.isEmpty()) {
            throw new IllegalStateException(
                    "holiday_calendar chưa có dữ liệu ngày lễ OFFICIAL — không tính được SLA (p4 B8)");
        }
        LocalDate horizon = today.atZone(UTC).toLocalDate().plusYears(1);
        LocalDate date = from.atZone(UTC).toLocalDate();
        int added = 0;
        while (added < days) {
            date = date.plusDays(1);
            if (date.isAfter(horizon)) {
                throw new IllegalStateException(
                        "holiday_calendar chưa có đủ dữ liệu ngày lễ để tính SLA (p4 B8) — "
                                + "yêu cầu DPO nạp ngày lễ của năm kế tiếp trước 31/12");
            }
            DayOfWeek dow = date.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY && !officialHolidays.contains(date)) {
                added++;
            }
        }
        return date.atTime(from.atZone(UTC).toLocalTime()).toInstant(UTC);
    }
}
