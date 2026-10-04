package com.catcheck.reminder.infrastructure.persistence;

import com.catcheck.reminder.domain.Reminder;
import com.catcheck.reminder.domain.ReminderSource;
import com.catcheck.reminder.domain.ReminderType;
import com.catcheck.reminder.domain.ScheduleKind;
import com.catcheck.reminder.domain.port.ReminderRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link ReminderRepository} trên {@code JdbcTemplate}, bảng {@code reminder} (V13).
 *
 * <p>Hai điều bắt buộc của repo này, dễ làm sai:</p>
 * <ul>
 *   <li>Đọc cột TIMESTAMPTZ bằng {@code rs.getObject(col, OffsetDateTime.class)} chứ KHÔNG
 *       {@code rs.getTimestamp()} — ArchUnit R13 cấm {@code java.sql.Timestamp}.</li>
 *   <li>{@code channels} là JSONB: ghi phải ép {@code ?::jsonb}, nếu không driver gửi kiểu
 *       {@code text} và Postgres từ chối.</li>
 * </ul>
 *
 * <p>Mọi câu lệnh đều mang {@code user_id = ?} — bất biến I14, không dựa vào tầng trên nhớ lọc.</p>
 */
@Repository
class JdbcReminderRepository implements ReminderRepository {

    private static final String COLUMNS = """
            id, user_id, cat_id, type, schedule_kind, interval_days, rrule,
            preferred_time_start, preferred_time_end, timezone, next_run_at, last_run_at,
            last_satisfied_at, channels, source, active, created_at, updated_at
            """;

    private final JdbcTemplate jdbc;

    JdbcReminderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Reminder insert(Reminder reminder) {
        UUID id = jdbc.queryForObject("""
                INSERT INTO reminder (user_id, cat_id, type, schedule_kind, interval_days, rrule,
                                      preferred_time_start, preferred_time_end, timezone,
                                      next_run_at, channels, source, active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                RETURNING id
                """,
                UUID.class,
                reminder.userId(), reminder.catId(), reminder.type().name(),
                reminder.scheduleKind().name(), reminder.intervalDays(), reminder.rrule(),
                reminder.preferredTimeStart(), reminder.preferredTimeEnd(), reminder.timezone(),
                reminder.nextRunAt(), toJsonArray(reminder.channels()),
                reminder.source().name(), reminder.active());
        return findByIdForUser(id, reminder.userId()).orElseThrow();
    }

    @Override
    public Optional<Reminder> findByIdForUser(UUID id, UUID userId) {
        return jdbc.query("SELECT " + COLUMNS + """
                  FROM reminder
                 WHERE id = ? AND user_id = ? AND deleted_at IS NULL
                """, (rs, rowNum) -> read(rs), id, userId).stream().findFirst();
    }

    @Override
    public List<Reminder> findAllForUser(UUID userId, UUID catIdFilter, Boolean activeFilter) {
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS
                + " FROM reminder WHERE user_id = ? AND deleted_at IS NULL");
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (catIdFilter != null) {
            sql.append(" AND cat_id = ?");
            args.add(catIdFilter);
        }
        if (activeFilter != null) {
            sql.append(" AND active = ?");
            args.add(activeFilter);
        }
        // Lịch đang bật lên trước, rồi tới mốc chạy gần nhất — khớp thứ tự màn M2 09.
        sql.append(" ORDER BY active DESC, next_run_at NULLS LAST, created_at DESC");
        return jdbc.query(sql.toString(), (rs, rowNum) -> read(rs), args.toArray());
    }

    @Override
    public Reminder update(Reminder reminder) {
        jdbc.update("""
                UPDATE reminder
                   SET schedule_kind = ?, interval_days = ?, rrule = ?,
                       preferred_time_start = ?, preferred_time_end = ?, next_run_at = ?,
                       channels = ?::jsonb, active = ?, updated_at = now()
                 WHERE id = ? AND user_id = ? AND deleted_at IS NULL
                """,
                reminder.scheduleKind().name(), reminder.intervalDays(), reminder.rrule(),
                reminder.preferredTimeStart(), reminder.preferredTimeEnd(), reminder.nextRunAt(),
                toJsonArray(reminder.channels()), reminder.active(),
                reminder.id(), reminder.userId());
        return findByIdForUser(reminder.id(), reminder.userId()).orElseThrow();
    }

    @Override
    public void softDelete(UUID id, UUID userId) {
        // active = false cùng lúc: partial unique index uq_reminder_cat_type_active lọc theo CẢ
        // active lẫn deleted_at, nhưng để active = true trên một dòng đã xoá mềm khiến mọi truy
        // vấn "đang bật" của job sau này phải nhớ thêm điều kiện.
        jdbc.update("""
                UPDATE reminder
                   SET deleted_at = now(), active = false, next_run_at = NULL, updated_at = now()
                 WHERE id = ? AND user_id = ? AND deleted_at IS NULL
                """, id, userId);
    }

    @Override
    public Optional<UUID> findActiveIdByCatAndType(UUID userId, UUID catId, String type) {
        return jdbc.query("""
                SELECT id FROM reminder
                 WHERE user_id = ? AND cat_id = ? AND type = ? AND active AND deleted_at IS NULL
                """, (rs, rowNum) -> rs.getObject("id", UUID.class), userId, catId, type)
                .stream().findFirst();
    }

    /* ------------------------------------------------------- scheduler (SendDueRemindersJob) */

    @Override
    public List<UUID> findDueIds(Instant now, int limit) {
        // Dùng đúng index partial ix_reminder_next_run (active AND deleted_at IS NULL).
        return jdbc.query("""
                SELECT id
                  FROM reminder
                 WHERE active
                   AND deleted_at IS NULL
                   AND type = 'SCAN_ROUTINE'
                   AND next_run_at IS NOT NULL
                   AND next_run_at <= ?
                 ORDER BY next_run_at
                 LIMIT ?
                """, (rs, rowNum) -> rs.getObject("id", UUID.class), now, limit);
    }

    @Override
    public Optional<Reminder> lockDue(UUID id, Instant now) {
        return jdbc.query("SELECT " + COLUMNS + """
                  FROM reminder
                 WHERE id = ?
                   AND active
                   AND deleted_at IS NULL
                   AND next_run_at IS NOT NULL
                   AND next_run_at <= ?
                   FOR UPDATE SKIP LOCKED
                """, (rs, rowNum) -> read(rs), id, now).stream().findFirst();
    }

    @Override
    public void markDispatched(UUID id, Instant sentAt, Instant nextRunAt) {
        jdbc.update("""
                UPDATE reminder
                   SET last_run_at = ?, next_run_at = ?, updated_at = now()
                 WHERE id = ?
                """, sentAt, nextRunAt, id);
    }

    private Reminder read(ResultSet rs) throws SQLException {
        return new Reminder(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("cat_id", UUID.class),
                ReminderType.valueOf(rs.getString("type")),
                ScheduleKind.valueOf(rs.getString("schedule_kind")),
                rs.getObject("interval_days", Integer.class),
                rs.getString("rrule"),
                rs.getObject("preferred_time_start", LocalTime.class),
                rs.getObject("preferred_time_end", LocalTime.class),
                rs.getString("timezone"),
                toInstant(rs.getObject("next_run_at", OffsetDateTime.class)),
                toInstant(rs.getObject("last_run_at", OffsetDateTime.class)),
                toInstant(rs.getObject("last_satisfied_at", OffsetDateTime.class)),
                parseJsonArray(rs.getString("channels")),
                ReminderSource.valueOf(rs.getString("source")),
                rs.getBoolean("active"),
                toInstant(rs.getObject("created_at", OffsetDateTime.class)),
                toInstant(rs.getObject("updated_at", OffsetDateTime.class)));
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    /**
     * {@code channels} luôn là mảng chuỗi phẳng (3 giá trị cố định ở {@code Reminder
     * .SUPPORTED_CHANNELS}), nên tự dựng/đọc JSON ở đây thay vì kéo {@code ObjectMapper} vào
     * repository — tránh phụ thuộc không cần thiết cho một hình dạng đã bị CHECK khoá chặt.
     */
    private static String toJsonArray(List<String> values) {
        return values.stream()
                .map(v -> "\"" + v + "\"")
                .reduce((a, b) -> a + "," + b)
                .map(joined -> "[" + joined + "]")
                .orElse("[]");
    }

    private static List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        String body = json.trim();
        if (body.startsWith("[")) {
            body = body.substring(1);
        }
        if (body.endsWith("]")) {
            body = body.substring(0, body.length() - 1);
        }
        if (body.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : body.split(",")) {
            out.add(part.trim().replace("\"", ""));
        }
        return List.copyOf(out);
    }
}
