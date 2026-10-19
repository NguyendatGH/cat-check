package com.catcheck.shared.job.application;

import com.catcheck.shared.job.JobContext;
import com.catcheck.shared.job.JobDescriptor;
import com.catcheck.shared.job.JobOutcome;
import com.catcheck.shared.job.JobProperties;
import com.catcheck.shared.job.ManualJobTrigger;
import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@ConditionalOnProperty(prefix = "catcheck.jobs.job-heartbeat-check", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class JobHeartbeatCheckJob implements ManualJobTrigger {

    public static final String JOB_NAME = JobProperties.JOB_HEARTBEAT_CHECK;

    private static final String OVERDUE_METRIC = "catcheck.job.overdue_count";

    private static final Duration GRACE = Duration.ofMinutes(1);

    private static final Logger log = LoggerFactory.getLogger(JobHeartbeatCheckJob.class);

    private final JobRunner jobRunner;
    private final JobRunRecorder recorder;
    private final JobProperties properties;
    private final AtomicInteger overdueCount = new AtomicInteger();

    public JobHeartbeatCheckJob(JobRunner jobRunner, JobRunRecorder recorder,
                                JobProperties properties, MeterRegistry meterRegistry) {
        this.jobRunner = jobRunner;
        this.recorder = recorder;
        this.properties = properties;
        meterRegistry.gauge(OVERDUE_METRIC, overdueCount);
    }

    @Scheduled(cron = "${catcheck.jobs.job-heartbeat-check.cron}", zone = "${catcheck.jobs.zone}")
    @SchedulerLock(
            name = JOB_NAME,
            lockAtMostFor = "${catcheck.jobs.job-heartbeat-check.lock-at-most-for}",
            lockAtLeastFor = "PT10S")
    public void run() {
        jobRunner.runScheduled(JOB_NAME, properties.jobHeartbeatCheck().dryRun(), this::check);
    }

    JobOutcome check(JobContext context) {
        List<String> overdue = new ArrayList<>();
        int inspected = 0;
        for (JobDescriptor descriptor : properties.scheduledJobs()) {
            if (!descriptor.enabled() || JOB_NAME.equals(descriptor.name())) {
                continue;
            }
            inspected++;
            if (isOverdue(descriptor, context.startedAt())) {
                overdue.add(descriptor.name());
            }
        }
        overdueCount.set(overdue.size());
        if (!overdue.isEmpty()) {
            log.warn("Co {} job qua han chua ghi job_run moi: {}", overdue.size(), overdue);
        }
        return new JobOutcome(
                com.catcheck.shared.job.JobRunStatus.SUCCESS, inspected, 0, 0,
                overdue.isEmpty() ? null : "overdue=" + String.join(",", overdue));
    }

    int overdueCount() {
        return overdueCount.get();
    }

    private boolean isOverdue(JobDescriptor descriptor, Instant now) {
        Optional<Instant> expected = previousExpectedRun(descriptor, now);
        if (expected.isEmpty()) {
            return false;
        }
        Optional<Instant> lastRun = recorder.lastStartedAt(descriptor.name());
        return lastRun.isEmpty() || lastRun.get().isBefore(expected.get().minus(GRACE));
    }

    private static Optional<Instant> previousExpectedRun(JobDescriptor descriptor, Instant now) {
        CronExpression cron = CronExpression.parse(descriptor.cron());
        ZonedDateTime reference = ZonedDateTime.ofInstant(now, descriptor.zone());
        ZonedDateTime first = cron.next(reference);
        if (first == null) {
            return Optional.empty();
        }
        ZonedDateTime second = cron.next(first);
        if (second == null) {
            return Optional.empty();
        }
        Duration period = Duration.between(first, second);
        return Optional.of(first.minus(period).toInstant());
    }


    @Override
    public String jobName() {
        return JOB_NAME;
    }

    @Override
    public JobOutcome runOnce(JobContext context) {
        return check(context);
    }
}
