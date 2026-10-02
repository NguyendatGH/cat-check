package com.catcheck.reminder.application;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Lệnh tầng application cho module {@code reminder} (p8 I2, I4). */
public final class ReminderCommands {

    private ReminderCommands() {
    }

    /** I2 — tạo lịch. */
    public record Create(
            UUID userId,
            UUID catId,
            String type,
            String scheduleKind,
            Integer intervalDays,
            String rrule,
            LocalTime preferredTimeStart,
            LocalTime preferredTimeEnd,
            String timezone,
            List<String> channels,
            String source) {
    }

    /**
     * I4 — merge-patch (RFC 7396). Mỗi field {@code null} nghĩa là "không đụng tới", nên không
     * phân biệt được với "đặt về null". p8 §8.3.2 chấp nhận đánh đổi này cho reminder: không
     * field nào trong đây có ngữ nghĩa "xoá về null" từ phía UI màn 09.
     */
    public record Patch(
            UUID userId,
            UUID reminderId,
            Integer intervalDays,
            String scheduleKind,
            String rrule,
            LocalTime preferredTimeStart,
            LocalTime preferredTimeEnd,
            List<String> channels,
            Boolean active) {
    }
}
