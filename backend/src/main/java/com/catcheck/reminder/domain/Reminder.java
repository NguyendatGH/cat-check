package com.catcheck.reminder.domain;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Một lịch nhắc theo dõi (p4 F1, bảng {@code reminder} ở V13).
 *
 * <p>Record bất biến: service dựng bản mới thay vì sửa tại chỗ. KHÔNG có annotation Spring nào
 * ở đây — ArchUnit R8 cấm {@code ..domain..} phụ thuộc Spring.</p>
 *
 * @param catId        null = nhắc cấp tài khoản (vd credit sắp hết hạn)
 * @param timezone     IANA tz SNAPSHOT lúc tạo. Cố ý không tham chiếu động tới
 *                     {@code app_user.timezone}: user đổi múi giờ thì lịch đã đặt không nên tự
 *                     nhảy giờ (p4 F1)
 * @param lastSatisfiedAt lần quét gần nhất thoả lịch — nguồn để tính "quá hạn N ngày" ở màn 09
 */
public record Reminder(
        UUID id,
        UUID userId,
        UUID catId,
        ReminderType type,
        ScheduleKind scheduleKind,
        Integer intervalDays,
        String rrule,
        LocalTime preferredTimeStart,
        LocalTime preferredTimeEnd,
        String timezone,
        Instant nextRunAt,
        Instant lastRunAt,
        Instant lastSatisfiedAt,
        List<String> channels,
        ReminderSource source,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    /** Kênh Phase 1 hỗ trợ (p4 F1 — Smartwatch/SMS trong design KHÔNG thuộc Phase 1). */
    public static final List<String> SUPPORTED_CHANNELS = List.of("PUSH", "EMAIL", "IN_APP");
}
