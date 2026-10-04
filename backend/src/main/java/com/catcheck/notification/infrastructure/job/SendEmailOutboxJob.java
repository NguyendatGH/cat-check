package com.catcheck.notification.infrastructure.job;

import com.catcheck.notification.application.EmailOutboxDispatcher;
import com.catcheck.notification.application.OutboxDispatchReport;
import com.catcheck.shared.job.JobContext;
import com.catcheck.shared.job.JobOutcome;
import com.catcheck.shared.job.JobProperties;
import com.catcheck.shared.job.application.JobRunner;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * {@code SendEmailOutboxJob} — p12 §12.6.3: đẩy {@code email_outbox} trạng thái {@code PENDING}
 * có {@code next_attempt_at <= now} qua SMTP, <b>mỗi 30 giây</b>, {@code lockAtMostFor = 25s}.
 *
 * <p>Cron {@code *&#47;30 * * * * *} là biểu thức 6 trường của Spring (có trường giây) — khác
 * cron 5 trường của Unix. (Dấu gạch chéo phải viết bằng entity trong javadoc, nếu không
 * {@code *}+{@code /} đóng luôn khối chú thích.)</p>
 *
 * <p><b>W2-B (H15.73): job đã chuyển lên nền {@code shared/job}.</b> Trước đó nó tự bắt ngoại
 * lệ và chỉ log — nghĩa là không có dòng {@code job_run} nào, trong khi p12 §12.6.1 quy tắc 4
 * đòi "mọi job ghi đúng một dòng {@code job_run} mỗi lần chạy" và màn "Log job nền" của p14
 * đọc đúng bảng đó. Cron, {@code lockAtMostFor} và hành vi backoff của dispatcher giữ nguyên;
 * chỉ phần khung chạy đổi. Công tắc nay là
 * {@code catcheck.jobs.send-email-outbox.enabled}.</p>
 *
 * <p><b>Đánh đổi đã biết:</b> 30 giây một dòng ⇒ ~2.880 dòng {@code job_run} mỗi ngày từ riêng
 * job này. Đó là cái giá của quy tắc 4; cần một job dọn {@code job_run} — xem handoff
 * H15.92.</p>
 */
@Component
@ConditionalOnProperty(prefix = "catcheck.jobs.send-email-outbox", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class SendEmailOutboxJob {

    /** Đúng tên ở p12 §12.6.3 — {@code job_run.job_name} không viết tắt. */
    public static final String JOB_NAME = JobProperties.SEND_EMAIL_OUTBOX;

    private final JobRunner jobRunner;
    private final EmailOutboxDispatcher dispatcher;
    private final JobProperties.JobSetting setting;

    public SendEmailOutboxJob(JobRunner jobRunner,
                              EmailOutboxDispatcher dispatcher,
                              JobProperties jobProperties) {
        this.jobRunner = jobRunner;
        this.dispatcher = dispatcher;
        this.setting = jobProperties.sendEmailOutbox();
    }

    @Scheduled(cron = "${catcheck.jobs.send-email-outbox.cron}", zone = "${catcheck.jobs.zone}")
    @SchedulerLock(
            name = JOB_NAME,
            lockAtMostFor = "${catcheck.jobs.send-email-outbox.lock-at-most-for}",
            lockAtLeastFor = "PT10S")
    public void run() {
        jobRunner.runScheduled(JOB_NAME, setting.dryRun(), this::dispatch);
    }

    /** Thân job — tách khỏi {@link #run()} để test gọi thẳng, không cần scheduler. */
    JobOutcome dispatch(JobContext context) {
        if (context.dryRun()) {
            // Dispatcher không có chế độ đếm-không-gửi: gửi email là tác dụng phụ ra ngoài hệ
            // thống, không mô phỏng được. dry-run ở đây = không làm gì, nói rõ trong nhật ký.
            return JobOutcome.of(0, 0, 0, "dry-run: khong gui email");
        }
        OutboxDispatchReport report = dispatcher.dispatchPending();
        // `failed` = dead letter (hết 5 lần thử, p12 §12.8.1) — đó mới là item hỏng thật sự;
        // `retried` còn cơ hội nên không kéo trạng thái lần chạy xuống PARTIAL.
        return JobOutcome.of(report.processed(), 0, report.failed(), summary(report));
    }

    private static String summary(OutboxDispatchReport report) {
        if (report.processed() == 0) {
            return null;
        }
        return "gui " + report.sent() + ", thu lai " + report.retried()
                + ", dead letter " + report.failed();
    }
}
