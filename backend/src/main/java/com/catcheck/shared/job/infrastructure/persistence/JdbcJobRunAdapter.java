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

/**
 * {@link JobRunPort} tren {@code JdbcTemplate}, bang {@code job_run} (p4 §K3).
 *
 * <p><b>Ba cho xuong thang truoc day DA HET (W5-D).</b> {@code V26__job_run_columns.sql} bo sung
 * {@code trigger_type}, {@code dry_run}, {@code items_processed}, {@code items_deleted},
 * {@code items_failed}, {@code instance_id} va mo {@code ck_job_run_status} cho
 * {@code SKIPPED_THRESHOLD} — tuc la dung cau truc p4 §K3 / p12 §12.8.2. Truoc do adapter nay
 * phai nhoi {@code "dry-run"} va {@code "trigger=MANUAL"} vao {@code error_summary} (H15.44),
 * nghia la mot lan chay THANH CONG do admin bam lai hien ra o man "Log job nen" cua p14 nhu mot
 * lan chay CO LOI — va L65 thi khong the lam dung viec p8 giao cho no
 * ({@code trigger_type = MANUAL}).</p>
 *
 * <p><b>{@code row_count} van duoc ghi song song voi {@code items_processed}.</b> Cot cu khong
 * bi xoa va khong bi bo trong: L64 ({@code JdbcOpsQueryAdapter}) dang doc no, va mot giai doan
 * man admin bi trong so lieu la cai gia khong can phai tra. Day la trung lap <b>co han</b> —
 * khi L64 chuyen sang doc {@code items_*} thi go cot cu o mot migration rieng (handoff
 * H15.180).</p>
 *
 * <p><b>{@code instance_id}</b> lay hostname cua may/container, cat con 64 ky tu theo kieu cot.
 * p4 §K3 goi no la thu de "debug khi chay nhieu instance"; o mot instance thi no la hang so vo
 * hai. Hostname <b>khong phai PII</b> — day la may chu, khong phai thiet bi nguoi dung.</p>
 */
@Repository
public class JdbcJobRunAdapter implements JobRunPort {

    private static final int INSTANCE_ID_MAX_LENGTH = 64;

    private static final String INSTANCE_ID = resolveInstanceId();

    private static final String INSERT_STARTED = """
            INSERT INTO job_run (id, job_name, status, trigger_type, dry_run, instance_id, started_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    /**
     * {@code ck_job_run_finish} buoc {@code finished_at} va {@code duration_ms} cung NULL hoac
     * cung co gia tri, nen hai cot dat trong CUNG mot cau lenh. {@code duration_ms} tinh ngay
     * trong SQL de khong phai truyen lai {@code started_at} va khong lech neu dong ho app troi.
     *
     * <p><b>{@code CAST(? AS text)} quanh tham so tom tat loi la BAT BUOC, khong phai trang
     * tri</b> (bug that, sua o W2-B — H15.87). Truoc day cau lenh dung {@code concat_ws} de gop
     * tom tat loi voi tien to dry-run; {@code concat_ws} la ham variadic {@code "any"} nen khi
     * driver gui mot NULL khong kieu, PostgreSQL nem
     * {@code could not determine data type of parameter} va cau {@code UPDATE} that bai — tuc
     * la <b>moi lan chay thanh cong</b> deu khong dong duoc dong, de lai
     * {@code status = 'RUNNING'} + {@code finished_at NULL} vinh vien (do that: 16/18 dong
     * {@code job_run} ket {@code RUNNING}). Tu V26 khong con phai gop chuoi nua — tom tat loi
     * ghi thang — nhung {@code CAST} duoc giu vi ly do GOC van dung: cot la {@code TEXT} va
     * driver khong suy duoc kieu cua mot {@code NULL} tran.</p>
     */
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
            // Dong vua duoc chinh JobRunRecorder mo ma khong tim thay la mau thuan noi tai:
            // nem de lo ngay thay vi de job im lang khong co nhat ky.
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

    /**
     * Hostname, hoac {@code "unknown"} neu khong phan giai duoc. {@code instance_id} la
     * {@code NOT NULL} o p4 §K3 nen khong duoc tra {@code null}; va mot lan DNS loi khong duoc
     * lam chet moi job nen ngoai le bi nuot o day mot cach co y.
     */
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
