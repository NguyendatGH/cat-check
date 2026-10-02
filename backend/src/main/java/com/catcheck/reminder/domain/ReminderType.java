package com.catcheck.reminder.domain;

/** Loại lịch nhắc — khớp CHECK {@code ck_reminder_type} của V13 (p4 F1). */
public enum ReminderType {
    /** Nhắc quét cát định kỳ. Bắt buộc gắn với một bé mèo. */
    SCAN_ROUTINE,
    /** Nhắc credit sắp hết hạn — cấp tài khoản, {@code catId} có thể null. */
    CREDIT_EXPIRY,
    /** Nhắc làm lại khảo sát sức khoẻ. */
    SURVEY_FOLLOWUP
}
