package com.catcheck.shared.job.application;

import com.catcheck.shared.job.JobContext;
import com.catcheck.shared.job.JobDescriptor;
import com.catcheck.shared.job.JobOutcome;
import com.catcheck.shared.job.JobProperties;
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

/**
 * <b>Nhóm E, p12 §12.6.6 — mỗi 10 phút ({@code 0 *&#47;10 * * * *}), {@code lockAtMostFor} 5m.</b>
 *
 * <p>So lịch kỳ vọng của mọi job đã đăng ký với dòng {@code job_run} gần nhất của nó và phát
 * {@code catcheck.job.overdue_count} (p12 §12.10). Đây là <b>cơ chế duy nhất</b> phát hiện "job
 * chết âm thầm": ShedLock không có dashboard, nên một job bị ngoại lệ ở bước đăng ký, hoặc một
 * khoá ShedLock kẹt, sẽ không để lại tín hiệu nào khác.</p>
 *
 * <p><b>Job này không tự giám sát chính mình</b> (p12 §12.6.6): nếu nó chết thì chính nó cũng
 * không chạy để báo. Việc canh nó thuộc uptime monitor bên ngoài (Part 18).</p>
 *
 * <p><b>Cách suy ra "lẽ ra đã chạy lúc nào".</b> {@code CronExpression} chỉ đi tới, không đi
 * lùi. Lấy hai mốc kế tiếp từ bây giờ, hiệu của chúng là chu kỳ, và mốc đáng lẽ gần nhất =
 * mốc kế tiếp trừ chu kỳ. Cách này đúng với mọi cron đều đặn trong danh mục p12 (mỗi giờ phút
 * :05, mỗi 10 phút, hằng ngày 02:00, Chủ Nhật 04:00) và không phải quét ngược từng phút.</p>
 */
@Component
@ConditionalOnProperty(prefix = "catcheck.jobs.job-heartbeat-check", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class JobHeartbeatCheckJob {

    /** Đúng tên ở p12 §12.6.6 — {@code job_run.job_name} không viết tắt. */
    public static final String JOB_NAME = JobProperties.JOB_HEARTBEAT_CHECK;

    /** p12 §12.10 — số job lẽ ra đã tới lịch nhưng chưa có dòng {@code job_run} mới. */
    private static final String OVERDUE_METRIC = "catcheck.job.overdue_count";

    /**
     * Biên dung sai trước khi coi một job là quá hạn. Lịch cron và lúc scheduler thực sự chạy
     * luôn lệch nhau vài giây (khởi động chậm, pool bận, đồng hồ instance trôi); không có biên
     * này thì metric nhấp nháy 0/1 mỗi chu kỳ và người trực sẽ học cách lờ nó đi.
     */
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

    /** Thân job — tách khỏi {@link #run()} để test gọi thẳng, không cần scheduler. */
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

    /** Số job đang quá hạn tại lần kiểm gần nhất — cùng giá trị với gauge, dùng cho test. */
    int overdueCount() {
        return overdueCount.get();
    }

    private boolean isOverdue(JobDescriptor descriptor, Instant now) {
        Optional<Instant> expected = previousExpectedRun(descriptor, now);
        if (expected.isEmpty()) {
            // Cron không sinh được hai mốc kế tiếp (ví dụ lịch một lần trong năm đã qua): không
            // đủ cơ sở để kết luận, và đoán bừa sẽ tạo cảnh báo giả.
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
}
