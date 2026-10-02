package com.catcheck.reminder.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Tính {@code nextRunAt} cho lịch {@link ScheduleKind#INTERVAL}.
 *
 * <p>Logic thuần, không Spring (ArchUnit R8). Hai điều p4 F1 yêu cầu và dễ làm sai:</p>
 * <ol>
 *   <li><b>Mốc đếm là lần quét gần nhất, không phải lịch cứng.</b> Nếu người dùng vừa quét thì
 *       đếm lại từ đó — nhắc cứng theo lịch sẽ spam đúng người dùng chăm chỉ.</li>
 *   <li><b>Giờ tính theo {@code timezone} snapshot của lịch</b>, rồi mới quy về UTC tuyệt đối để
 *       scheduler quét bằng một index duy nhất.</li>
 * </ol>
 *
 * <p>{@link ScheduleKind#RRULE} chưa tính được ở đây: cần một thư viện RFC 5545 mà
 * {@code context/spec/reference/research-integrations.md} chưa pin version nào, và quy tắc cứng
 * của repo là không tự chọn version. Lịch RRULE vì thế trả {@code null} — scheduler bỏ qua, và
 * {@code ReminderService} ghi rõ điều đó cho client qua {@code nextRunAt = null}.</p>
 */
public final class NextRunCalculator {

    private NextRunCalculator() {
    }

    /**
     * @param from  mốc bắt đầu đếm: {@code lastSatisfiedAt} nếu có, nếu không thì "bây giờ"
     * @return mốc UTC tuyệt đối, hoặc {@code null} khi lịch không phải INTERVAL
     */
    public static Instant nextRun(Reminder reminder, Instant from) {
        if (reminder.scheduleKind() != ScheduleKind.INTERVAL || reminder.intervalDays() == null) {
            return null;
        }
        ZoneId zone = ZoneId.of(reminder.timezone());
        LocalTime at = reminder.preferredTimeStart() != null ? reminder.preferredTimeStart() : LocalTime.of(9, 0);
        LocalDate day = from.atZone(zone).toLocalDate().plusDays(reminder.intervalDays());
        ZonedDateTime candidate = day.atTime(at).atZone(zone);
        // Khoảng giờ mong muốn đã trôi qua trong ngày hôm đó thì đẩy sang chu kỳ kế tiếp —
        // không nhắc vào một mốc đã nằm trong quá khứ.
        ZonedDateTime now = from.atZone(zone);
        while (!candidate.toInstant().isAfter(now.toInstant())) {
            candidate = candidate.plusDays(reminder.intervalDays());
        }
        return candidate.toInstant();
    }
}
