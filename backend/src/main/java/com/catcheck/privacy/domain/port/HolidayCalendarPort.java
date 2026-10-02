package com.catcheck.privacy.domain.port;

import com.catcheck.privacy.domain.HolidayCalendarEntry;

import java.util.Set;

/**
 * Cổng đọc {@code holiday_calendar} (p4 B8) — chỉ ngày lễ {@code OFFICIAL} được dùng tính
 * SLA; {@code COMPANY} không kéo dài SLA luật định.
 */
public interface HolidayCalendarPort {

    /** Tất cả ngày lễ đã nạp (cả OFFICIAL lẫn COMPANY) — service lọc theo source. Rỗng nghĩa là bảng chưa được nạp, tính SLA phải lỗi (p4 B8). */
    Set<HolidayCalendarEntry> allHolidays();
}
