package com.catcheck.reminder.domain;

/**
 * Cách biểu diễn lịch — khớp CHECK {@code ck_reminder_schedule_kind} của V13.
 *
 * <p>p4 F1 giữ cả hai vì UI chính (M2 màn 09) chỉ cho chọn mỗi N ngày ({@link #INTERVAL} là đủ
 * và dễ tính {@code nextRunAt}), còn lịch gợi ý lúc onboarding (M1 {@code 01c-4}, "Sáng Thứ Tư
 * & Chiều Chủ Nhật") cần RRULE. KHÔNG được điền cả hai.</p>
 */
public enum ScheduleKind {
    INTERVAL,
    RRULE
}
