package com.catcheck.reminder.application;

import com.catcheck.reminder.api.ReminderErrorCode;
import com.catcheck.reminder.application.spi.CatOwnershipPort;
import com.catcheck.reminder.application.spi.ReminderFeaturePort;
import com.catcheck.reminder.domain.NextRunCalculator;
import com.catcheck.reminder.domain.Reminder;
import com.catcheck.reminder.domain.ReminderSource;
import com.catcheck.reminder.domain.ReminderType;
import com.catcheck.reminder.domain.ScheduleKind;
import com.catcheck.reminder.domain.port.ReminderRepository;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.ConflictException;
import com.catcheck.shared.error.NotFoundException;
import com.catcheck.shared.error.PermissionDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.Clock;
import java.time.DateTimeException;
import java.util.List;
import java.util.UUID;

/**
 * Nghiệp vụ lịch nhắc — p8 nhóm I (I1–I6).
 *
 * <p>{@code @Transactional} chỉ đặt ở tầng này (ArchUnit R12).</p>
 *
 * <p>Mọi endpoint của nhóm I đều gắn {@code E:reminder} nên cổng entitlement được kiểm ở ĐẦU
 * mỗi phương thức công khai, kể cả phương thức đọc — p11 §11.5.5 cấm cache kết quả trong phiên.</p>
 */
@Service
public class ReminderService {

    private static final String FEATURE_KEY = "reminder";

    private final ReminderRepository repository;
    private final CatOwnershipPort catOwnershipPort;
    private final ReminderFeaturePort featurePort;
    private final Clock clock;

    public ReminderService(ReminderRepository repository,
                           CatOwnershipPort catOwnershipPort,
                           ReminderFeaturePort featurePort,
                           Clock clock) {
        this.repository = repository;
        this.catOwnershipPort = catOwnershipPort;
        this.featurePort = featurePort;
        this.clock = clock;
    }

    /** I1 — danh sách, lọc theo {@code catId}/{@code active}. */
    @Transactional(readOnly = true)
    public List<Reminder> list(UUID userId, UUID catId, Boolean active) {
        requireFeature(userId);
        return repository.findAllForUser(userId, catId, active);
    }

    /** I3 — chi tiết. */
    @Transactional(readOnly = true)
    public Reminder detail(UUID userId, UUID reminderId) {
        requireFeature(userId);
        return repository.findByIdForUser(reminderId, userId)
                .orElseThrow(() -> new NotFoundException(ReminderErrorCode.REMINDER_NOT_FOUND));
    }

    /** I2 — tạo lịch. */
    @Transactional
    public Reminder create(ReminderCommands.Create cmd) {
        requireFeature(cmd.userId());

        ReminderType type = parseEnum(ReminderType.class, cmd.type());
        ScheduleKind kind = cmd.scheduleKind() == null
                ? ScheduleKind.INTERVAL
                : parseEnum(ScheduleKind.class, cmd.scheduleKind());
        ReminderSource source = cmd.source() == null
                ? ReminderSource.USER
                : parseEnum(ReminderSource.class, cmd.source());

        // CHECK ck_reminder_scan_needs_cat của V13 cũng ép điều này, nhưng chặn sớm ở đây để
        // client nhận 400 có nghĩa thay vì 500 từ DataIntegrityViolation.
        if (type == ReminderType.SCAN_ROUTINE && cmd.catId() == null) {
            throw new BusinessRuleException(ReminderErrorCode.REMINDER_SCHEDULE_INVALID, "catId");
        }
        if (cmd.catId() != null && !catOwnershipPort.isOwnedAndAlive(cmd.catId(), cmd.userId())) {
            throw new NotFoundException(ReminderErrorCode.CAT_NOT_FOUND);
        }
        validateSchedule(kind, cmd.intervalDays(), cmd.rrule());
        validateTimeWindow(cmd.preferredTimeStart(), cmd.preferredTimeEnd());
        List<String> channels = validateChannels(cmd.channels());
        String timezone = validateTimezone(cmd.timezone());

        // Bất biến I23 được ép bởi partial unique index. Kiểm trước CHỈ để trả 409 kèm
        // existingReminderId cho UI (p8 §8.2.4 dòng REMINDER_LIMIT_REACHED) — index vẫn là
        // nguồn ép thật, hai request song song sẽ bị DB chặn.
        if (cmd.catId() != null) {
            repository.findActiveIdByCatAndType(cmd.userId(), cmd.catId(), type.name())
                    .ifPresent(existing -> {
                        throw new ConflictException(
                                ReminderErrorCode.REMINDER_LIMIT_REACHED, type.name(), existing.toString());
                    });
        }

        Instant now = clock.instant();
        Reminder draft = new Reminder(
                null, cmd.userId(), cmd.catId(), type, kind, cmd.intervalDays(), cmd.rrule(),
                cmd.preferredTimeStart(), cmd.preferredTimeEnd(), timezone,
                null, null, null, channels, source, true, now, now);
        return repository.insert(withNextRun(draft, now));
    }

    /** I4 — merge-patch: sửa / bật / tắt. */
    @Transactional
    public Reminder patch(ReminderCommands.Patch cmd) {
        requireFeature(cmd.userId());
        Reminder current = repository.findByIdForUser(cmd.reminderId(), cmd.userId())
                .orElseThrow(() -> new NotFoundException(ReminderErrorCode.REMINDER_NOT_FOUND));

        ScheduleKind kind = cmd.scheduleKind() == null
                ? current.scheduleKind()
                : parseEnum(ScheduleKind.class, cmd.scheduleKind());
        Integer intervalDays = cmd.intervalDays() != null ? cmd.intervalDays() : current.intervalDays();
        String rrule = cmd.rrule() != null ? cmd.rrule() : current.rrule();
        LocalTime start = cmd.preferredTimeStart() != null ? cmd.preferredTimeStart() : current.preferredTimeStart();
        LocalTime end = cmd.preferredTimeEnd() != null ? cmd.preferredTimeEnd() : current.preferredTimeEnd();
        List<String> channels = cmd.channels() != null ? validateChannels(cmd.channels()) : current.channels();
        boolean active = cmd.active() != null ? cmd.active() : current.active();

        validateSchedule(kind, intervalDays, rrule);
        validateTimeWindow(start, end);

        // Bật lại một lịch đã tắt có thể đụng bất biến I23 nếu trong lúc tắt user đã tạo lịch
        // khác cùng type. Index partial sẽ chặn; kiểm trước để trả 409 có nghĩa.
        if (active && !current.active() && current.catId() != null) {
            repository.findActiveIdByCatAndType(cmd.userId(), current.catId(), current.type().name())
                    .filter(existing -> !existing.equals(current.id()))
                    .ifPresent(existing -> {
                        throw new ConflictException(
                                ReminderErrorCode.REMINDER_LIMIT_REACHED,
                                current.type().name(), existing.toString());
                    });
        }

        Instant now = clock.instant();
        Reminder updated = new Reminder(
                current.id(), current.userId(), current.catId(), current.type(), kind, intervalDays, rrule,
                start, end, current.timezone(), current.nextRunAt(), current.lastRunAt(),
                current.lastSatisfiedAt(), channels, current.source(), active, current.createdAt(), now);
        // Lịch tắt thì không có mốc chạy kế tiếp — để scheduler không phải lọc thêm điều kiện.
        Instant from = updated.lastSatisfiedAt() != null ? updated.lastSatisfiedAt() : now;
        return repository.update(active ? withNextRun(updated, from) : withNextRun(updated, from, true));
    }

    /** I5 — xoá mềm. */
    @Transactional
    public void delete(UUID userId, UUID reminderId) {
        requireFeature(userId);
        repository.findByIdForUser(reminderId, userId)
                .orElseThrow(() -> new NotFoundException(ReminderErrorCode.REMINDER_NOT_FOUND));
        repository.softDelete(reminderId, userId);
    }

    private static final DateTimeFormatter ICS_UTC =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneId.of("UTC"));

    /**
     * I6 — nội dung file {@code .ics} cho một lịch.
     *
     * <p>Sinh ở tầng application chứ không ở controller vì ArchUnit R4 cấm {@code @RestController}
     * nhận/trả type trong {@code ..domain..}; controller chỉ cầm {@code String}.</p>
     *
     * <p>File TĨNH một VEVENT ở mốc {@code nextRunAt} (p12 §12.5.6, C24), KHÔNG sinh chuỗi lặp:
     * {@code nextRunAt} được tính lại sau mỗi lần user quét (p4 F1), nên một RRULE xuất ra đây
     * sẽ lệch khỏi lịch thật ngay lần quét kế tiếp.</p>
     */
    @Transactional(readOnly = true)
    public String calendarIcs(UUID userId, UUID reminderId) {
        Reminder reminder = detail(userId, reminderId);
        Instant stamp = clock.instant();
        Instant start = reminder.nextRunAt() != null ? reminder.nextRunAt() : stamp;
        Instant end = start.plusSeconds(1800);
        // Mô tả thuần hành động theo dõi — KHÔNG hứa hẹn y tế (p17 REQ-COPY-01).
        return """
                BEGIN:VCALENDAR
                VERSION:2.0
                PRODID:-//CatCheck//Reminder//VI
                CALSCALE:GREGORIAN
                BEGIN:VEVENT
                UID:%s@catcheck.vn
                DTSTAMP:%s
                DTSTART:%s
                DTEND:%s
                SUMMARY:CatCheck - den lich quet cat ve sinh
                DESCRIPTION:Mo ung dung CatCheck de quet mau cat va ghi nhan chi so pH.
                END:VEVENT
                END:VCALENDAR
                """.formatted(
                reminder.id(), ICS_UTC.format(stamp), ICS_UTC.format(start), ICS_UTC.format(end));
    }

    private Reminder withNextRun(Reminder reminder, Instant from) {
        return withNextRun(reminder, from, false);
    }

    private Reminder withNextRun(Reminder reminder, Instant from, boolean clear) {
        Instant next = clear ? null : NextRunCalculator.nextRun(reminder, from);
        return new Reminder(
                reminder.id(), reminder.userId(), reminder.catId(), reminder.type(), reminder.scheduleKind(),
                reminder.intervalDays(), reminder.rrule(), reminder.preferredTimeStart(),
                reminder.preferredTimeEnd(), reminder.timezone(), next, reminder.lastRunAt(),
                reminder.lastSatisfiedAt(), reminder.channels(), reminder.source(), reminder.active(),
                reminder.createdAt(), reminder.updatedAt());
    }

    private void requireFeature(UUID userId) {
        if (!featurePort.isReminderEnabled(userId)) {
            throw new PermissionDeniedException(ReminderErrorCode.FEATURE_NOT_IN_PLAN, FEATURE_KEY);
        }
    }

    private void validateSchedule(ScheduleKind kind, Integer intervalDays, String rrule) {
        if (kind == ScheduleKind.INTERVAL) {
            if (intervalDays == null || intervalDays < 1 || intervalDays > 90) {
                throw new BusinessRuleException(ReminderErrorCode.REMINDER_SCHEDULE_INVALID, "intervalDays");
            }
        } else if (rrule == null || rrule.isBlank()) {
            throw new BusinessRuleException(ReminderErrorCode.REMINDER_SCHEDULE_INVALID, "rrule");
        }
    }

    private void validateTimeWindow(LocalTime start, LocalTime end) {
        if (start != null && end != null && !end.isAfter(start)) {
            throw new BusinessRuleException(ReminderErrorCode.REMINDER_SCHEDULE_INVALID, "preferredTimeEnd");
        }
    }

    private List<String> validateChannels(List<String> channels) {
        if (channels == null || channels.isEmpty()) {
            return List.of("PUSH");
        }
        for (String channel : channels) {
            if (!Reminder.SUPPORTED_CHANNELS.contains(channel)) {
                throw new BusinessRuleException(ReminderErrorCode.REMINDER_SCHEDULE_INVALID, "channels");
            }
        }
        return List.copyOf(channels);
    }

    private String validateTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return "Asia/Ho_Chi_Minh";
        }
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException ex) {
            throw new BusinessRuleException(ReminderErrorCode.REMINDER_SCHEDULE_INVALID, "timezone");
        }
        return timezone;
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String raw) {
        try {
            return Enum.valueOf(type, raw);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BusinessRuleException(
                    ReminderErrorCode.REMINDER_SCHEDULE_INVALID, type.getSimpleName());
        }
    }
}
