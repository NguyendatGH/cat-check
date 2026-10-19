package com.catcheck.shared.job.application;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.catcheck.shared.id.UuidV7;
import com.catcheck.shared.job.JobOutcome;
import com.catcheck.shared.job.JobProperties;
import com.catcheck.shared.job.JobRunStatus;
import com.catcheck.shared.job.JobTriggerType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobRunnerTest {

    private static final Instant NOW = Instant.parse("2026-10-03T05:05:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final FakeJobRunPort port = new FakeJobRunPort();
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private JobRunner runner(boolean masterSwitch) {
        JobRunRecorder recorder = new JobRunRecorder(port, new UuidV7(CLOCK), CLOCK);
        return new JobRunner(recorder, properties(masterSwitch), meterRegistry, CLOCK);
    }

    private static JobProperties properties(boolean masterSwitch) {
        JobProperties.JobSetting setting = new JobProperties.JobSetting(
                true, "0 5 * * * *", Duration.ofMinutes(50), 500, 5_000, false);
        return new JobProperties(masterSwitch, ZoneId.of("Asia/Ho_Chi_Minh"),
                setting, setting, setting, setting, setting, setting, setting, setting);
    }

    @Test
    void successPathClosesTheRowAndRecordsDuration() {
        JobOutcome outcome = runner(true).runScheduled("ExpireCreditBatchesJob", false,
                context -> JobOutcome.of(10, 0, 0, null));

        assertEquals(JobRunStatus.SUCCESS, outcome.status());
        FakeJobRunPort.Row row = port.only();
        assertEquals(JobRunStatus.SUCCESS, row.status());
        assertEquals(10, row.itemsProcessed());
        assertEquals(NOW, row.finishedAt());
        assertNotNull(meterRegistry.find("catcheck.job.duration")
                .tag("job_name", "ExpireCreditBatchesJob").timer(),
                "p12 §12.10 doi metric catcheck.job.duration theo job_name");
    }

    @Test
    void exceptionInTheBodyStillClosesTheRowAsFailed() {
        JobOutcome outcome = runner(true).runScheduled("ExpireCreditBatchesJob", false,
                context -> {
                    throw new IllegalStateException("khong ket noi duoc DB");
                });

        assertEquals(JobRunStatus.FAILED, outcome.status());
        FakeJobRunPort.Row row = port.only();
        assertEquals(JobRunStatus.FAILED, row.status());
        assertEquals(NOW, row.finishedAt(), "dong khong duoc ket o trang thai RUNNING");
        assertTrue(row.errorSummary().startsWith("IllegalStateException"));
        assertTrue(row.errorSummary().contains("khong ket noi duoc DB"));
    }

    @Test
    void failureSummaryAndLogDoNotCarryPiiFromTheExceptionMessage() {
        Logger logger = (Logger) LoggerFactory.getLogger(JobRunner.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            runner(true).runScheduled("SendEmailOutboxJob", false, context -> {
                throw new IllegalStateException("550 rejected <person@example.com>",
                        new IllegalArgumentException("Key (email)=(person@example.com) already exists"));
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        String summary = port.only().errorSummary();
        assertTrue(summary.startsWith("IllegalStateException"));
        assertTrue(summary.contains("550 rejected"), "van giu ly do de admin doc duoc");
        assertFalse(summary.contains("person@example.com"));

        ILoggingEvent failure = appender.list.stream()
                .filter(event -> event.getThrowableProxy() != null)
                .findFirst()
                .orElseThrow();
        for (IThrowableProxy proxy = failure.getThrowableProxy(); proxy != null; proxy = proxy.getCause()) {
            assertFalse(String.valueOf(proxy.getMessage()).contains("person@example.com"));
            assertTrue(proxy.getStackTraceElementProxyArray().length > 0, "van giu stack frame de debug");
        }
    }

    @Test
    void errorInTheBodyClosesTheRowThenPropagates() {
        JobRunner runner = runner(true);

        assertThrows(StackOverflowError.class, () -> runner.runScheduled("ExpireCreditBatchesJob", false,
                context -> {
                    throw new StackOverflowError();
                }));

        FakeJobRunPort.Row row = port.only();
        assertEquals(JobRunStatus.FAILED, row.status());
        assertEquals(NOW, row.finishedAt(), "dong khong duoc ket o trang thai RUNNING");
        assertEquals("StackOverflowError", row.errorSummary());
    }

    @Test
    void masterSwitchOffWritesNoRowAtAll() {
        runner(false).runScheduled("ExpireCreditBatchesJob", false,
                context -> {
                    throw new AssertionError("than job khong duoc chay khi cong tac tong tat");
                });

        assertTrue(port.rows.isEmpty());
    }

    @Test
    void dryRunFlagReachesTheRowAndTheBody() {
        runner(true).runScheduled("ExpireCreditBatchesJob", true, context -> {
            assertTrue(context.dryRun(), "than job phai doc duoc co dry-run tu JobContext");
            return JobOutcome.success(3);
        });

        FakeJobRunPort.Row row = port.only();
        assertTrue(row.dryRun());
        assertEquals(3, row.itemsProcessed());
    }

    @Test
    void manualTriggerIsRecorded() {
        runner(true).run("ExpireCreditBatchesJob", JobTriggerType.MANUAL, false,
                context -> JobOutcome.success(0));

        assertEquals(JobTriggerType.MANUAL, port.only().triggerType());
    }
}
