package com.catcheck.admin.infrastructure.persistence;

import com.catcheck.admin.domain.JobRunRow;
import com.catcheck.admin.domain.OutboxRow;
import com.catcheck.admin.domain.port.OpsQueryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link OpsQueryPort} trên {@code JdbcTemplate} — L64, L66. Chỉ {@code SELECT}.
 *
 * <p>L66 hợp nhất hai bảng bằng {@code UNION ALL} rồi mới sắp xếp và phân trang. Không gọi hai
 * truy vấn rồi trộn ở Java: trộn ở Java thì không thể phân trang đúng (trang 2 của "cả hai
 * kênh" không phải là hợp của trang 2 từng kênh), và tổng số dòng cũng sai.</p>
 *
 * <p>{@code TIMESTAMPTZ} đọc qua {@code getObject(col, OffsetDateTime.class)} — không dùng
 * {@code getTimestamp()}, vốn suy mốc giờ theo {@code user.timezone} của JVM (R13).</p>
 */
@Repository
public class JdbcOpsQueryAdapter implements OpsQueryPort {

    private static final String JOB_RUN_COLUMNS =
            "id, job_name, status, trigger_type, dry_run, started_at, finished_at, duration_ms, "
                    + "row_count, items_processed, items_deleted, items_failed, instance_id, error_summary";

    /**
     * Hai nhánh của L66. {@code email_outbox} không có {@code last_error_code} còn
     * {@code notification_outbox} không có {@code template_code} — mỗi nhánh tự quy về cùng bộ
     * cột để {@code UNION ALL} hợp lệ.
     */
    private static final String OUTBOX_UNION = """
            SELECT id,
                   'EMAIL'                          AS channel,
                   status,
                   template_code                    AS reference,
                   to_address                       AS recipient,
                   attempts,
                   next_attempt_at,
                   last_error,
                   sent_at,
                   created_at
              FROM email_outbox
            UNION ALL
            SELECT id,
                   'PUSH'                           AS channel,
                   status,
                   notification_id::text            AS reference,
                   push_subscription_id::text       AS recipient,
                   attempts,
                   next_attempt_at,
                   coalesce(last_error_code || ': ', '') || coalesce(last_error, '') AS last_error,
                   sent_at,
                   created_at
              FROM notification_outbox
            """;

    private final JdbcTemplate jdbc;

    public JdbcOpsQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<JobRunRow> findJobRuns(String jobName, String status, int offset, int limit) {
        List<Object> args = new ArrayList<>();
        String where = jobRunWhere(jobName, status, args);
        args.add(limit);
        args.add(offset);
        return jdbc.query("SELECT " + JOB_RUN_COLUMNS + " FROM job_run" + where
                        + " ORDER BY started_at DESC, id LIMIT ? OFFSET ?",
                (rs, rowNum) -> mapJobRun(rs), args.toArray());
    }

    @Override
    public long countJobRuns(String jobName, String status) {
        List<Object> args = new ArrayList<>();
        String where = jobRunWhere(jobName, status, args);
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM job_run" + where, Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    @Override
    public List<OutboxRow> findOutbox(String channel, String status, int offset, int limit) {
        List<Object> args = new ArrayList<>();
        String where = outboxWhere(channel, status, args);
        args.add(limit);
        args.add(offset);
        return jdbc.query("SELECT * FROM (" + OUTBOX_UNION + ") o" + where
                        + " ORDER BY created_at DESC, id LIMIT ? OFFSET ?",
                (rs, rowNum) -> mapOutbox(rs), args.toArray());
    }

    @Override
    public long countOutbox(String channel, String status) {
        List<Object> args = new ArrayList<>();
        String where = outboxWhere(channel, status, args);
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM (" + OUTBOX_UNION + ") o" + where, Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    private static String jobRunWhere(String jobName, String status, List<Object> args) {
        StringBuilder where = new StringBuilder();
        if (notBlank(jobName)) {
            append(where, "job_name = ?");
            args.add(jobName.strip());
        }
        if (notBlank(status)) {
            append(where, "status = ?");
            args.add(status.strip());
        }
        return where.toString();
    }

    private static String outboxWhere(String channel, String status, List<Object> args) {
        StringBuilder where = new StringBuilder();
        if (notBlank(channel)) {
            append(where, "o.channel = ?");
            args.add(channel.strip());
        }
        if (notBlank(status)) {
            append(where, "o.status = ?");
            args.add(status.strip());
        }
        return where.toString();
    }

    private static void append(StringBuilder where, String condition) {
        where.append(where.isEmpty() ? " WHERE " : " AND ").append(condition);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private JobRunRow mapJobRun(ResultSet rs) throws SQLException {
        return new JobRunRow(
                rs.getObject("id", java.util.UUID.class),
                rs.getString("job_name"),
                rs.getString("status"),
                rs.getString("trigger_type"),
                rs.getBoolean("dry_run"),
                instant(rs, "started_at"),
                instant(rs, "finished_at"),
                integerOrNull(rs, "duration_ms"),
                integerOrNull(rs, "row_count"),
                integerOrNull(rs, "items_processed"),
                integerOrNull(rs, "items_deleted"),
                integerOrNull(rs, "items_failed"),
                rs.getString("instance_id"),
                rs.getString("error_summary"));
    }

    private OutboxRow mapOutbox(ResultSet rs) throws SQLException {
        String lastError = rs.getString("last_error");
        return new OutboxRow(
                rs.getObject("id", java.util.UUID.class),
                rs.getString("channel"),
                rs.getString("status"),
                rs.getString("reference"),
                rs.getString("recipient"),
                rs.getInt("attempts"),
                instant(rs, "next_attempt_at"),
                lastError == null || lastError.isBlank() ? null : lastError,
                instant(rs, "sent_at"),
                instant(rs, "created_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static Integer integerOrNull(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
