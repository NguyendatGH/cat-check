package com.catcheck.privacy.domain;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Một ngày nghỉ trong {@code holiday_calendar} (p4 B8) — dùng tính SLA DSAR theo
 * <b>ngày làm việc</b>, loại trừ T7, CN và ngày lễ.
 *
 * <p>Bảng phải được nạp trước go-live và nạp thêm mỗi năm; năm sau chưa nạp thì hàm tính
 * SLA phải <b>báo lỗi rõ ràng</b> chứ không âm thầm coi mọi ngày là ngày làm việc — coi
 * nhầm làm SLA tính ngắn hơn thực tế, tức tự đặt mình vào thế vi phạm.</p>
 *
 * @param holidayDate ngày nghỉ
 * @param countryCode 'VN' — có sẵn cột để sau này mở thị trường khác không cần migration
 * @param nameVy      tên ngày lễ tiếng Việt
 * @param source       OFFICIAL (dùng tính SLA) / COMPANY (KHÔNG kéo dài SLA luật định)
 * @param createdAt    mốc tạo
 */
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
