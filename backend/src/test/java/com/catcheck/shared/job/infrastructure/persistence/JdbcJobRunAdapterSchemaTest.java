package com.catcheck.shared.job.infrastructure.persistence;

import com.catcheck.shared.job.JobOutcome;
import com.catcheck.shared.job.JobRunStatus;
import com.catcheck.shared.job.JobTriggerType;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code V26__job_run_columns.sql} + {@link JdbcJobRunAdapter} tren PostgreSQL THAT.
 *
 * <p><b>Vi sao phai la PostgreSQL that chu khong phai fake:</b> dieu dang kiem o day la
 * <b>schema</b> — cac cot ma {@code V15__ops.sql} con thieu so voi p4 §K3 (handoff H15.44), rang
 * buoc {@code ck_job_run_status} co nhan {@code SKIPPED_THRESHOLD} khong, va rang buoc
 * {@code ck_job_run_finish} (buoc {@code finished_at} va {@code duration_ms} cung NULL hoac cung
 * co gia tri). Mot fake trong bo nho khong co rang buoc nao nen se xanh ngay ca khi migration
 * sai. Image ghim {@code postgres:18.6-trixie} theo {@code research-integrations.md}, giong
 * {@code SchemaInvariantTests}.</p>
 */
@Testcontainers
class JdbcJobRunAdapterSchemaTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-trixie");

    private static JdbcTemplate jdbc;
    private static JdbcJobRunAdapter adapter;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/seed")
                .load()
                .migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        dataSource.setDriverClassName("org.postgresql.Driver");
        jdbc = new JdbcTemplate(dataSource);
        adapter = new JdbcJobRunAdapter(jdbc);
    }

    @Test
    @DisplayName("V26 tao du 6 cot ma p4 §K3 / p12 §12.8.2 doi (H15.44)")
    void v26AddsEveryColumnThatPart4Requires() {
        assertThat(columnNames()).contains(
                "trigger_type", "dry_run", "items_processed", "items_deleted", "items_failed",
                "instance_id");
    }

    @Test
    @DisplayName("ck_job_run_status nhan SKIPPED_THRESHOLD — p4 §K3: no KHONG phai FAILED")
    void statusCheckAcceptsSkippedThreshold() {
        UUID runId = UUID.randomUUID();
        adapter.insertStarted(runId, "CleanupDeadPushTokensJob", JobTriggerType.SCHEDULE, true,
                Instant.parse("2026-10-06T19:00:00Z"));

        adapter.finish(runId, JobOutcome.skippedThreshold(10_000, "nguong an toan 20%: 3000/10000"),
                Instant.parse("2026-10-06T19:00:05Z"));

        assertThat(jdbc.queryForObject(
                "SELECT status FROM job_run WHERE id = ?", String.class, runId))
                .isEqualTo("SKIPPED_THRESHOLD");
    }

    @Test
    @DisplayName("L65 ghi trigger_type = MANUAL THAT, khong con nhoi vao error_summary (H15.44)")
    void manualTriggerIsStoredInItsOwnColumn() {
        UUID runId = UUID.randomUUID();
        adapter.insertStarted(runId, "SendEmailOutboxJob", JobTriggerType.MANUAL, false,
                Instant.parse("2026-10-06T19:10:00Z"));
        adapter.finish(runId, JobOutcome.of(12, 3, 1, "dead letter 1"),
                Instant.parse("2026-10-06T19:10:02Z"));

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT trigger_type, dry_run, items_processed, items_deleted, items_failed,
                       row_count, instance_id, error_summary, status, duration_ms
                  FROM job_run WHERE id = ?
                """, runId);

        assertThat(row).containsEntry("trigger_type", "MANUAL")
                .containsEntry("dry_run", false)
                .containsEntry("items_processed", 12)
                .containsEntry("items_deleted", 3)
                .containsEntry("items_failed", 1)
                // Cot cu duoc ghi song song trong giai doan chuyen tiep (H15.180).
                .containsEntry("row_count", 12)
                .containsEntry("status", "PARTIAL");
        assertThat((String) row.get("error_summary"))
                .as("error_summary chi con tom tat loi that — khong con 'trigger=MANUAL'")
                .isEqualTo("dead letter 1");
        assertThat((String) row.get("instance_id")).isNotBlank();
        assertThat((Integer) row.get("duration_ms")).isEqualTo(2_000);
    }

    @Test
    @DisplayName("dry-run VAN de lai mot dong, voi dry_run = true (p4 §K3, p15 REQ-RET-01)")
    void dryRunLeavesARowFlaggedDryRun() {
        UUID runId = UUID.randomUUID();
        adapter.insertStarted(runId, "CleanupDeadPushTokensJob", JobTriggerType.MANUAL, true,
                Instant.parse("2026-10-06T19:20:00Z"));
        adapter.finish(runId, JobOutcome.of(42, 0, 0, "dry-run: 42 dong du dieu kien"),
                Instant.parse("2026-10-06T19:20:01Z"));

        assertThat(jdbc.queryForObject(
                "SELECT dry_run FROM job_run WHERE id = ?", Boolean.class, runId)).isTrue();
    }

    @Test
    @DisplayName("ck_job_run_trigger_type chan gia tri ngoai SCHEDULE|MANUAL|EVENT")
    void triggerTypeCheckRejectsUnknownValues() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO job_run (id, job_name, status, trigger_type, instance_id, started_at)
                VALUES (?, 'X', 'RUNNING', 'BAM_BUA', 'host', now())
                """, UUID.randomUUID()))
                .hasMessageContaining("ck_job_run_trigger_type");
    }

    @Test
    @DisplayName("Index phan vung cho FAILED + SKIPPED_THRESHOLD ton tai (p4 §K3 index #2)")
    void partialIndexForRowsNeedingAttentionExists() {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_indexes
                 WHERE tablename = 'job_run' AND indexname = 'idx_job_run_needs_attention'
                """, Integer.class)).isEqualTo(1);
    }

    private static java.util.List<String> columnNames() {
        return jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                 WHERE table_schema = 'public' AND table_name = 'job_run'
                """, String.class);
    }
}
