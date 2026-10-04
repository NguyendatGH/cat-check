package com.catcheck.shared.job.application;

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

/**
 * Khuôn chạy chung của <b>mọi</b> job nghiệp vụ — p12 §12.6.1 quy tắc 4 đòi "đúng một dòng
 * {@code job_run} mỗi lần chạy", và cách rẻ nhất để điều đó luôn đúng là không job nào tự ghi.
 *
 * <p>Một lần gọi {@link #run} làm đúng bốn việc: (1) tôn trọng công tắc bật/tắt, (2) mở dòng
 * {@code job_run}, (3) chạy thân job rồi đóng dòng với trạng thái/số đếm tương ứng, (4) phát
 * metric {@code catcheck.job.duration} (p12 §12.10).</p>
 *
 * <p><b>Ngoại lệ không bao giờ thoát ra khỏi đây.</b> Thân job chạy trong luồng scheduler; để
 * ngoại lệ lọt ra thì Spring chỉ log rồi quên, và quan trọng hơn là dòng {@code job_run} sẽ kẹt
 * vĩnh viễn ở {@code RUNNING} nên {@code JobHeartbeatCheckJob} tưởng job vẫn sống. Bắt tại đây
 * rồi đóng dòng với {@link com.catcheck.shared.job.JobRunStatus#FAILED} là cách duy nhất để
 * "job chết" trở thành dữ liệu quan sát được.</p>
 *
 * <p>Tóm tắt lỗi ghi xuống DB chỉ gồm <b>tên lớp ngoại lệ + message đã cắt ngắn</b>; stack trace
 * đi vào log ứng dụng (p4 §K3). Không ghi tham số nghiệp vụ nào vào tóm tắt — tránh PII lọt vào
 * một bảng mà admin panel hiển thị công khai.</p>
 */
@Component
public class JobRunner {

    private static final Logger log = LoggerFactory.getLogger(JobRunner.class);

    /** p12 §12.10 — timer theo {@code job_name}, để cảnh báo trước khi chạm {@code lockAtMostFor}. */
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

    /**
     * Chạy một job theo khuôn chung.
     *
     * @param jobName     đúng tên job ở p12 §12.6, không viết tắt
     * @param triggerType scheduler / admin bấm tay / sự kiện
     * @param dryRun      chỉ đếm, không ghi — thân job phải tự tôn trọng cờ này
     * @param body        thân job; nhận {@link JobContext} (có {@code job_run.id}) và trả về số đếm
     * @return kết quả đã ghi vào {@code job_run}
     */
    public JobOutcome run(
            String jobName,
            JobTriggerType triggerType,
            boolean dryRun,
            Function<JobContext, JobOutcome> body
    ) {
        if (!properties.enabled()) {
            // Công tắc tổng tắt: KHÔNG ghi job_run. Một dòng "đã chạy nhưng không làm gì" sẽ
            // khiến JobHeartbeatCheckJob báo job khoẻ mạnh trong khi nó đang bị tắt chủ ý.
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
            log.error("Job {} that bai (runId={})", jobName, context.runId(), exception);
            outcome = JobOutcome.failed(summarize(exception));
        }

        recorder.finish(context.runId(), outcome);
        recordDuration(jobName, Duration.between(context.startedAt(), clock.instant()));
        log.info("Job {} ket thuc: status={} processed={} deleted={} failed={} dryRun={}",
                jobName, outcome.status(), outcome.itemsProcessed(), outcome.itemsDeleted(),
                outcome.itemsFailed(), dryRun);
        return outcome;
    }

    /** Lần chạy theo lịch, không dry-run — dạng gọi của hầu hết job. */
    public JobOutcome runScheduled(String jobName, boolean dryRun, Function<JobContext, JobOutcome> body) {
        return run(jobName, JobTriggerType.SCHEDULE, dryRun, body);
    }

    /** Mốc bắt đầu theo {@code Clock} đã inject — thân job không được tự gọi {@code Instant.now()} (R13). */
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
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getClass().getSimpleName() + ": " + message;
    }
}
