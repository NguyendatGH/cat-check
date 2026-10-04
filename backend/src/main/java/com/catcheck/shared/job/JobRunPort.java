package com.catcheck.shared.job;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Cổng ghi/đọc bảng {@code job_run} — <b>bảng nhật ký job DUY NHẤT</b> của hệ thống
 * (p12 §12.8.2; {@code retention_run_log} đã bị bỏ, p4 §4.1.7 liệt nó vào danh sách tên bị cấm).
 *
 * <p>Ranh giới transaction KHÔNG thuộc cổng này: {@code JobRunRecorder} mở
 * {@code REQUIRES_NEW} quanh từng thao tác để dòng {@code job_run} vẫn còn sau khi transaction
 * nghiệp vụ rollback — nếu không, lần chạy thất bại sẽ không để lại dấu vết nào, đúng lúc cần
 * nhất.</p>
 */
public interface JobRunPort {

    /** Mở một dòng {@code job_run} ở trạng thái {@link JobRunStatus#RUNNING}. */
    void insertStarted(UUID runId, String jobName, JobTriggerType triggerType, boolean dryRun, Instant startedAt);

    /** Đóng dòng đã mở: trạng thái cuối, các số đếm, tóm tắt lỗi và {@code finished_at}. */
    void finish(UUID runId, JobOutcome outcome, Instant finishedAt);

    /**
     * {@code started_at} của lần chạy gần nhất của một job — nền tảng của
     * {@code JobHeartbeatCheckJob} (p12 §12.6.6). Rỗng nghĩa là job CHƯA từng chạy.
     */
    Optional<Instant> findLastStartedAt(String jobName);
}
