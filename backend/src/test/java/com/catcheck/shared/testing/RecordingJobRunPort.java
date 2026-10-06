package com.catcheck.shared.testing;

import com.catcheck.shared.job.JobOutcome;
import com.catcheck.shared.job.JobRunPort;
import com.catcheck.shared.job.JobRunStatus;
import com.catcheck.shared.job.JobTriggerType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link JobRunPort} trong bộ nhớ, dùng chung cho test của module nghiệp vụ.
 *
 * <p>{@code shared.job.application.FakeJobRunPort} đã có nhưng là package-private, nên module
 * khác không gọi được. Bản này giữ cả trạng thái dòng (mở rồi đóng) để test kiểm chứng được
 * "đúng một dòng {@code job_run} mỗi lần chạy" (p12 §12.6.1 quy tắc 4).</p>
 */
public final class RecordingJobRunPort implements JobRunPort {

    /** Một dòng {@code job_run} trong bộ nhớ. */
    public record Row(UUID runId, String jobName, JobRunStatus status,
                      Integer itemsProcessed, Integer itemsFailed, Instant finishedAt) {
    }

    private final List<Row> rows = new ArrayList<>();

    public List<Row> rows() {
        return List.copyOf(rows);
    }

    public Row only() {
        if (rows.size() != 1) {
            throw new IllegalStateException("Mong doi dung 1 dong job_run, co " + rows.size());
        }
        return rows.getFirst();
    }

    @Override
    public void insertStarted(UUID runId, String jobName, JobTriggerType triggerType,
                              boolean dryRun, Instant startedAt) {
        rows.add(new Row(runId, jobName, JobRunStatus.RUNNING, null, null, null));
    }

    @Override
    public void finish(UUID runId, JobOutcome outcome, Instant finishedAt) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).runId().equals(runId)) {
                rows.set(i, new Row(runId, rows.get(i).jobName(), outcome.status(),
                        outcome.itemsProcessed(), outcome.itemsFailed(), finishedAt));
                return;
            }
        }
        throw new IllegalStateException("Dong job_run chua mo: " + runId);
    }

    @Override
    public Optional<Instant> findLastStartedAt(String jobName) {
        return rows.stream()
                .filter(row -> row.jobName().equals(jobName))
                .map(row -> Instant.EPOCH)
                .findFirst();
    }
}
