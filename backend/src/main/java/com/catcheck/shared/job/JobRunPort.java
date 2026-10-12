package com.catcheck.shared.job;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface JobRunPort {

    void insertStarted(UUID runId, String jobName, JobTriggerType triggerType, boolean dryRun, Instant startedAt);

    void finish(UUID runId, JobOutcome outcome, Instant finishedAt);

    Optional<Instant> findLastStartedAt(String jobName);
}
