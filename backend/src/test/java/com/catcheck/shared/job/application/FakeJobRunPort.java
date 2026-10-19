package com.catcheck.shared.job.application;

import com.catcheck.shared.job.JobOutcome;
import com.catcheck.shared.job.JobRunPort;
import com.catcheck.shared.job.JobRunStatus;
import com.catcheck.shared.job.JobTriggerType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class FakeJobRunPort implements JobRunPort {

    record Row(UUID runId, String jobName, JobTriggerType triggerType, boolean dryRun,
               Instant startedAt, JobRunStatus status, Integer itemsProcessed,
               Integer itemsDeleted, Integer itemsFailed, String errorSummary, Instant finishedAt) {
    }

    final List<Row> rows = new ArrayList<>();

    boolean failOnInsert;

    @Override
    public void insertStarted(
            UUID runId, String jobName, JobTriggerType triggerType, boolean dryRun, Instant startedAt) {
        if (failOnInsert) {
            throw new IllegalStateException("khong mo duoc job_run");
        }
        rows.add(new Row(runId, jobName, triggerType, dryRun, startedAt,
                JobRunStatus.RUNNING, null, null, null, null, null));
    }

    @Override
    public void finish(UUID runId, JobOutcome outcome, Instant finishedAt) {
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (row.runId().equals(runId)) {
                rows.set(i, new Row(row.runId(), row.jobName(), row.triggerType(), row.dryRun(),
                        row.startedAt(), outcome.status(), outcome.itemsProcessed(),
                        outcome.itemsDeleted(), outcome.itemsFailed(), outcome.errorSummary(),
                        finishedAt));
                return;
            }
        }
        throw new IllegalStateException("Khong tim thay dong job_run: " + runId);
    }

    @Override
    public Optional<Instant> findLastStartedAt(String jobName) {
        return rows.stream()
                .filter(row -> row.jobName().equals(jobName))
                .map(Row::startedAt)
                .max(Instant::compareTo);
    }

    void seedFinishedRun(String jobName, Instant startedAt) {
        rows.add(new Row(UUID.randomUUID(), jobName, JobTriggerType.SCHEDULE, false, startedAt,
                JobRunStatus.SUCCESS, 0, 0, 0, null, startedAt));
    }

    Row only() {
        if (rows.size() != 1) {
            throw new IllegalStateException("Mong doi dung 1 dong job_run, co " + rows.size());
        }
        return rows.getFirst();
    }
}
