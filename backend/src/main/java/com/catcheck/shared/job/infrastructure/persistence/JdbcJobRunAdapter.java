package com.catcheck.shared.job.infrastructure.persistence;

import com.catcheck.shared.job.JobOutcome;
import com.catcheck.shared.job.JobRunPort;
import com.catcheck.shared.job.JobRunStatus;
import com.catcheck.shared.job.JobTriggerType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcJobRunAdapter implements JobRunPort {

    private static final int INSTANCE_ID_MAX_LENGTH = 64;

    private static final String INSTANCE_ID = resolveInstanceId();

    private static final String INSERT_STARTED = """
            INSERT INTO job_run (id, job_name, status, trigger_type, dry_run, instance_id, started_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String FINISH = """
            UPDATE job_run
               SET status          = ?,
                   finished_at     = CAST(? AS timestamptz),
                   duration_ms     = GREATEST(0, CAST(EXTRACT(EPOCH FROM
                                         (CAST(? AS timestamptz) - started_at)) * 1000 AS INT)),
                   row_count       = ?,
                   items_processed = ?,
                   items_deleted   = ?,
                   items_failed    = ?,
                   error_summary   = CAST(? AS text)
             WHERE id = ?
            """;

    private static final String LAST_STARTED_AT = """
            SELECT started_at FROM job_run WHERE job_name = ? ORDER BY started_at DESC LIMIT 1
            """;

    private final JdbcTemplate jdbc;

    public JdbcJobRunAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertStarted(
            UUID runId, String jobName, JobTriggerType triggerType, boolean dryRun, Instant startedAt) {
        jdbc.update(INSERT_STARTED,
                runId,
                jobName,
                JobRunStatus.RUNNING.name(),
                (triggerType == null ? JobTriggerType.SCHEDULE : triggerType).name(),
                dryRun,
                INSTANCE_ID,
                utc(startedAt));
    }

    @Override
    public void finish(UUID runId, JobOutcome outcome, Instant finishedAt) {
        OffsetDateTime finished = utc(finishedAt);
        int updated = jdbc.update(FINISH,
                outcome.status().name(),
                finished,
                finished,
                outcome.itemsProcessed(),
                outcome.itemsProcessed(),
                outcome.itemsDeleted(),
                outcome.itemsFailed(),
                outcome.errorSummary(),
                runId);
        if (updated != 1) {
            throw new IllegalStateException("Khong dong duoc dong job_run: " + runId);
        }
    }

    @Override
    public Optional<Instant> findLastStartedAt(String jobName) {
        List<OffsetDateTime> rows = jdbc.query(LAST_STARTED_AT,
                (rs, rowNum) -> rs.getObject("started_at", OffsetDateTime.class), jobName);
        return rows.isEmpty() || rows.getFirst() == null
                ? Optional.empty()
                : Optional.of(rows.getFirst().toInstant());
    }

    private static String resolveInstanceId() {
        String candidate = System.getenv("HOSTNAME");
        if (candidate == null || candidate.isBlank()) {
            try {
                candidate = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException ex) {
                candidate = null;
            }
        }
        if (candidate == null || candidate.isBlank()) {
            return "unknown";
        }
        return candidate.length() <= INSTANCE_ID_MAX_LENGTH
                ? candidate
                : candidate.substring(0, INSTANCE_ID_MAX_LENGTH);
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
