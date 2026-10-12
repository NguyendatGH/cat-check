package com.catcheck.shared.job.application;

import com.catcheck.shared.id.UuidV7;
import com.catcheck.shared.job.JobContext;
import com.catcheck.shared.job.JobOutcome;
import com.catcheck.shared.job.JobRunPort;
import com.catcheck.shared.job.JobTriggerType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class JobRunRecorder {

    private final JobRunPort jobRunPort;
    private final UuidV7 uuidV7;
    private final Clock clock;

    public JobRunRecorder(JobRunPort jobRunPort, UuidV7 uuidV7, Clock clock) {
        this.jobRunPort = jobRunPort;
        this.uuidV7 = uuidV7;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public JobContext start(String jobName, JobTriggerType triggerType, boolean dryRun) {
        Instant startedAt = clock.instant();
        UUID runId = uuidV7.generate();
        jobRunPort.insertStarted(runId, jobName, triggerType, dryRun, startedAt);
        return new JobContext(runId, jobName, startedAt, dryRun);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(UUID runId, JobOutcome outcome) {
        jobRunPort.finish(runId, outcome, clock.instant());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<Instant> lastStartedAt(String jobName) {
        return jobRunPort.findLastStartedAt(jobName);
    }
}
