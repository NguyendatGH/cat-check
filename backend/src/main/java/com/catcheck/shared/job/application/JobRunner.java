package com.catcheck.shared.job.application;

import com.catcheck.shared.application.TelemetryScrubber;
import com.catcheck.shared.error.RedactedThrowable;
import com.catcheck.shared.job.JobContext;
import com.catcheck.shared.job.JobOutcome;
import com.catcheck.shared.job.JobProperties;
import com.catcheck.shared.job.JobTriggerType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;

@Component
public class JobRunner {

    private static final Logger log = LoggerFactory.getLogger(JobRunner.class);

    private static final String DURATION_METRIC = "catcheck.job.duration";

    private final JobRunRecorder recorder;
    private final JobProperties properties;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public JobRunner(
            JobRunRecorder recorder,
            JobProperties properties,
            MeterRegistry meterRegistry,
            Clock clock
    ) {
        this.recorder = recorder;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    public JobOutcome run(
            String jobName,
            JobTriggerType triggerType,
            boolean dryRun,
            Function<JobContext, JobOutcome> body
    ) {
        if (!properties.enabled()) {
            log.debug("Bo qua job {} — catcheck.jobs.enabled=false", jobName);
            return JobOutcome.success(0);
        }

        JobContext context = recorder.start(jobName, triggerType, dryRun);
        JobOutcome outcome;
        try {
            outcome = body.apply(context);
            if (outcome == null) {
                outcome = JobOutcome.success(0);
            }
        } catch (RuntimeException exception) {
            log.error("Job {} that bai (runId={})", jobName, context.runId(), RedactedThrowable.of(exception));
            outcome = JobOutcome.failed(summarize(exception));
        } catch (Error error) {
            log.error("Job {} that bai (runId={})", jobName, context.runId(), RedactedThrowable.of(error));
            try {
                recorder.finish(context.runId(), JobOutcome.failed(error.getClass().getSimpleName()));
            } catch (RuntimeException finishFailure) {
                error.addSuppressed(finishFailure);
            }
            throw error;
        }

        recorder.finish(context.runId(), outcome);
        recordDuration(jobName, Duration.between(context.startedAt(), clock.instant()));
        log.info("Job {} ket thuc: status={} processed={} deleted={} failed={} dryRun={}",
                jobName, outcome.status(), outcome.itemsProcessed(), outcome.itemsDeleted(),
                outcome.itemsFailed(), dryRun);
        return outcome;
    }

    public JobOutcome runScheduled(String jobName, boolean dryRun, Function<JobContext, JobOutcome> body) {
        return run(jobName, JobTriggerType.SCHEDULE, dryRun, body);
    }

    public Instant now() {
        return clock.instant();
    }

    private void recordDuration(String jobName, Duration elapsed) {
        Timer.builder(DURATION_METRIC)
                .tag("job_name", jobName)
                .register(meterRegistry)
                .record(elapsed);
    }

    private static String summarize(RuntimeException exception) {
        String message = TelemetryScrubber.scrubText(exception.getMessage(), 0);
        return message == null
                ? exception.getClass().getSimpleName()
                : exception.getClass().getSimpleName() + ": " + message;
    }
}
