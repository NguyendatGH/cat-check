package com.catcheck.shared.job.infrastructure.persistence;

import com.catcheck.shared.job.JobOutcome;
import com.catcheck.shared.job.JobRunPort;
import com.catcheck.shared.job.JobRunStatus;
import com.catcheck.shared.job.JobTriggerType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link JobRunPort} trên {@code JdbcTemplate}, bảng {@code job_run} (p4 §K3).
 *
 * <p><b>Bảng hiện có HẸP HƠN đặc tả.</b> {@code V15__ops.sql} tạo {@code job_run} với
 * {@code (id, job_name, status, started_at, finished_at, duration_ms, row_count, error_summary)},
 * trong khi p4 §K3 và p12 §12.8.2 còn đòi {@code trigger_type}, {@code dry_run},
 * {@code items_processed}, {@code items_deleted}, {@code items_failed}, {@code instance_id} và
 * trạng thái {@code SKIPPED_THRESHOLD}. W1-B <b>không được thêm migration</b> (thư mục
 * {@code db/migration} do W1-A giữ), nên adapter này ghi những gì cột hiện có cho phép và phần
 * còn lại đã được ghi vào {@code context/spec/reviews/handoffs.md} mục H15.e. Ba chỗ xuống
 * thang, đều cố ý và đều tạm thời:</p>
 * <ol>
 *   <li>{@code items_processed} ghi vào {@code row_count}; {@code items_deleted} và
 *       {@code items_failed} <b>chưa lưu được</b> (chúng vẫn có trong {@link JobOutcome} và
 *       trong log ứng dụng).</li>
 *   <li>{@code dry_run} ghi tạm thành tiền tố {@code "dry-run"} trong {@code error_summary} —
 *       xấu nhưng đọc được bằng mắt ở màn "Log job nền" của p14, còn hơn mất hẳn thông tin
 *       "lần chạy này chỉ đếm" (p4 §K3: lần chạy dry-run vẫn phải để lại dấu vết).</li>
 *   <li>{@link JobRunStatus#SKIPPED_THRESHOLD} ghi thành {@code 'SKIPPED'} vì
 *       {@code ck_job_run_status} hiện chỉ cho phép giá trị đó; {@code trigger_type} ghi vào
 *       {@code error_summary} cùng tiền tố dry-run khi khác {@code SCHEDULE}.</li>
 * </ol>
 */
@Repository
public class JdbcJobRunAdapter implements JobRunPort {

    private static final String INSERT_STARTED = """
            INSERT INTO job_run (id, job_name, status, started_at, error_summary)
            VALUES (?, ?, ?, ?, ?)
            """;

    /**
     * {@code ck_job_run_finish} buộc {@code finished_at} và {@code duration_ms} cùng NULL hoặc
     * cùng có giá trị, nên hai cột đặt trong CÙNG một câu lệnh. {@code duration_ms} tính ngay
     * trong SQL để không phải truyền lại {@code started_at} và không lệch nếu đồng hồ app trôi.
     *
     * <p>{@code concat_ws} bỏ qua NULL, nên nó gộp tiền tố đã ghi lúc mở dòng (dry-run /
     * trigger type) với tóm tắt lỗi mà không sinh dấu "; " thừa; {@code NULLIF(..., '')} trả
     * cột về NULL khi cả hai đều rỗng.</p>
     *
     * <p><b>{@code CAST(? AS text)} quanh tham số tóm tắt lỗi là BẮT BUỘC, không phải trang
     * trí</b> (bug thật, sửa ở W2-B — H15.87). {@code concat_ws} là hàm variadic
     * {@code "any"}, nên khi driver gửi một NULL không kiểu, PostgreSQL ném
     * {@code could not determine data type of parameter $5} và câu {@code UPDATE} thất bại —
     * tức là <b>mọi lần chạy thành công</b> (lần chạy không có tóm tắt lỗi) đều không đóng
     * được dòng, để lại {@code status = 'RUNNING'} + {@code finished_at NULL} vĩnh viễn. Lỗi
     * này vô hình vì {@code JobRunner} chỉ bắt ngoại lệ của THÂN job, còn ngoại lệ của
     * {@code finish()} thoát ra tới {@code @Scheduled} và bị nuốt. Đo thật trên DB local:
     * 16/18 dòng {@code job_run} kẹt {@code RUNNING}; đúng hai dòng {@code SUCCESS} là của
     * {@code JobHeartbeatCheckJob}, job duy nhất luôn có {@code error_summary} khác NULL
     * (danh sách job quá hạn) nên vô tình né được bug.</p>
     */
    private static final String FINISH = """
            UPDATE job_run
               SET status        = ?,
                   finished_at   = CAST(? AS timestamptz),
                   duration_ms   = GREATEST(0, CAST(EXTRACT(EPOCH FROM
                                       (CAST(? AS timestamptz) - started_at)) * 1000 AS INT)),
                   row_count     = ?,
                   error_summary = NULLIF(concat_ws('; ', error_summary, CAST(? AS text)), '')
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
                utc(startedAt),
                marker(triggerType, dryRun));
    }

    @Override
    public void finish(UUID runId, JobOutcome outcome, Instant finishedAt) {
        OffsetDateTime finished = utc(finishedAt);
        int updated = jdbc.update(FINISH,
                storedStatus(outcome.status()),
                finished,
                finished,
                outcome.itemsProcessed(),
                outcome.errorSummary(),
                runId);
        if (updated != 1) {
            // Dòng vừa được chính JobRunRecorder mở mà không tìm thấy là mâu thuẫn nội tại:
            // ném để lộ ngay thay vì để job im lặng không có nhật ký.
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
     * Ghi tạm {@code trigger_type} + {@code dry_run} khi hai cột đó chưa tồn tại (H15.e).
     * Trả {@code null} cho lần chạy thường theo lịch để không làm bẩn cột lỗi.
     */
    private static String marker(JobTriggerType triggerType, boolean dryRun) {
        StringBuilder marker = new StringBuilder();
        if (dryRun) {
            marker.append("dry-run");
        }
        if (triggerType != null && triggerType != JobTriggerType.SCHEDULE) {
            if (!marker.isEmpty()) {
                marker.append(' ');
            }
            marker.append("trigger=").append(triggerType.name());
        }
        return marker.isEmpty() ? null : marker.toString();
    }

    /**
     * {@code ck_job_run_status} hiện chỉ nhận
     * {@code RUNNING|SUCCESS|FAILED|PARTIAL|SKIPPED}, chưa có {@code SKIPPED_THRESHOLD} (H15.e).
     */
    private static String storedStatus(JobRunStatus status) {
        return status == JobRunStatus.SKIPPED_THRESHOLD ? "SKIPPED" : status.name();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
