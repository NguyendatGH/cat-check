package com.catcheck.shared.job.application;

import com.catcheck.shared.id.UuidV7;
import com.catcheck.shared.job.JobContext;
import com.catcheck.shared.job.JobOutcome;
import com.catcheck.shared.job.JobProperties;
import com.catcheck.shared.job.JobRunStatus;
import com.catcheck.shared.job.JobTriggerType;
import com.catcheck.shared.job.ManualJobTrigger;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Nen cua L65 {@code POST /admin/jobs/{jobName}/run}.
 *
 * <p>Dieu quan trong nhat duoc kiem o day: mot lan chay do admin bam de lai <b>dung mot</b> dong
 * {@code job_run} voi {@code trigger_type = MANUAL} (p4 §K3, p12 §12.6.1 quy tac 4). Day la ly do
 * duy nhat {@code V26__job_run_columns.sql} ton tai — khong co cot {@code trigger_type} that thi
 * L65 khong lam dung duoc viec p8 giao cho no.</p>
 */
class ManualJobLauncherTest {

    private static final Instant NOW = Instant.parse("2026-10-06T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final FakeJobRunPort port = new FakeJobRunPort();

    private static JobProperties properties(boolean masterSwitch) {
        JobProperties.JobSetting setting = new JobProperties.JobSetting(
                true, "0 5 * * * *", Duration.ofMinutes(50), 500, 5_000, false);
        return new JobProperties(masterSwitch, ZoneId.of("Asia/Ho_Chi_Minh"),
                setting, setting, setting, setting, setting, setting, setting, setting);
    }

    private ManualJobLauncher launcher(boolean masterSwitch, ManualJobTrigger... triggers) {
        JobProperties props = properties(masterSwitch);
        JobRunner runner = new JobRunner(
                new JobRunRecorder(port, new UuidV7(CLOCK), CLOCK), props,
                new SimpleMeterRegistry(), CLOCK);
        return new ManualJobLauncher(runner, props, List.of(triggers));
    }

    @Test
    @DisplayName("L65 ghi dung MOT dong job_run voi trigger_type = MANUAL")
    void manualRunRecordsExactlyOneRowWithManualTrigger() {
        ManualJobTrigger trigger = trigger(JobProperties.SEND_EMAIL_OUTBOX,
                context -> JobOutcome.of(7, 0, 0, null));

        JobOutcome outcome = launcher(true, trigger).run(trigger, false);

        assertThat(outcome.status()).isEqualTo(JobRunStatus.SUCCESS);
        assertThat(port.rows).hasSize(1);
        FakeJobRunPort.Row row = port.rows.getFirst();
        assertThat(row.triggerType())
                .as("p8 L65 + p4 §K3: admin bam ⇒ trigger_type = MANUAL")
                .isEqualTo(JobTriggerType.MANUAL);
        assertThat(row.jobName()).isEqualTo(JobProperties.SEND_EMAIL_OUTBOX);
        assertThat(row.dryRun()).isFalse();
        assertThat(row.itemsProcessed()).isEqualTo(7);
        assertThat(row.finishedAt()).as("dong phai duoc DONG, khong ket o RUNNING").isEqualTo(NOW);
    }

    @Test
    @DisplayName("dry-run VAN de lai mot dong job_run (p4 §K3, p15 REQ-RET-01)")
    void dryRunStillLeavesARow() {
        ManualJobTrigger trigger = trigger(JobProperties.CLEANUP_DEAD_PUSH_TOKENS,
                context -> {
                    // Than job phai nhan duoc co dry-run, neu khong no se xoa that.
                    assertThat(context.dryRun()).isTrue();
                    return JobOutcome.of(42, 0, 0, "dry-run: 42 dong du dieu kien");
                });

        launcher(true, trigger).run(trigger, true);

        assertThat(port.rows).hasSize(1);
        assertThat(port.rows.getFirst().dryRun()).isTrue();
        assertThat(port.rows.getFirst().triggerType()).isEqualTo(JobTriggerType.MANUAL);
    }

    @Test
    @DisplayName("Than job nem van dong duoc dong thanh FAILED — khong de admin doan")
    void exceptionStillClosesTheRowAsFailed() {
        ManualJobTrigger trigger = trigger(JobProperties.SEND_EMAIL_OUTBOX,
                context -> {
                    throw new IllegalStateException("SMTP khong phan hoi");
                });

        JobOutcome outcome = launcher(true, trigger).run(trigger, false);

        assertThat(outcome.status()).isEqualTo(JobRunStatus.FAILED);
        assertThat(port.rows.getFirst().status()).isEqualTo(JobRunStatus.FAILED);
        assertThat(port.rows.getFirst().errorSummary()).contains("SMTP khong phan hoi");
    }

    @Test
    @DisplayName("Ten job la va job chua dang ky duong chay tay la HAI trang thai khac nhau")
    void unknownJobAndUnregisteredJobAreDistinguishable() {
        ManualJobLauncher launcher = launcher(true,
                trigger(JobProperties.SEND_EMAIL_OUTBOX, context -> JobOutcome.success(0)));

        // (1) khong co trong danh muc p12 §12.6 cua ban build nay
        assertThat(launcher.isKnownJob("KhongCoJobNaoTenVay")).isFalse();
        assertThat(launcher.findRunnable("KhongCoJobNaoTenVay")).isEmpty();

        // (2) co trong danh muc nhung chua co ManualJobTrigger — van la job THAT
        assertThat(launcher.isKnownJob(JobProperties.EXPIRE_CREDIT_BATCHES)).isTrue();
        assertThat(launcher.findRunnable(JobProperties.EXPIRE_CREDIT_BATCHES)).isEmpty();

        // (3) co ca hai
        assertThat(launcher.findRunnable(JobProperties.SEND_EMAIL_OUTBOX)).isPresent();
        assertThat(launcher.runnableJobNames()).containsExactly(JobProperties.SEND_EMAIL_OUTBOX);
    }

    @Test
    @DisplayName("Cong tac tong tat ⇒ jobsEnabled() = false, de controller tra 409 thay vi im lang")
    void masterSwitchIsVisibleToTheCaller() {
        assertThat(launcher(false).jobsEnabled()).isFalse();
        assertThat(launcher(true).jobsEnabled()).isTrue();
    }

    @Test
    @DisplayName("Hai trigger cung jobName lam app khong khoi dong — khong de hai than tranh mot dong")
    void duplicateJobNamesFailFast() {
        assertThatThrownBy(() -> launcher(true,
                trigger(JobProperties.SEND_EMAIL_OUTBOX, context -> JobOutcome.success(0)),
                trigger(JobProperties.SEND_EMAIL_OUTBOX, context -> JobOutcome.success(0))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(JobProperties.SEND_EMAIL_OUTBOX);
    }

    private static ManualJobTrigger trigger(
            String jobName, java.util.function.Function<JobContext, JobOutcome> body) {
        return new ManualJobTrigger() {
            @Override
            public String jobName() {
                return jobName;
            }

            @Override
            public JobOutcome runOnce(JobContext context) {
                return body.apply(context);
            }
        };
    }
}
