package com.catcheck.shared.job;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

/**
 * Cấu hình job nền — bật/tắt, lịch, kích thước lô, chế độ {@code dry_run}.
 *
 * <p><b>p12 §12.6 không đặt tên khoá YAML</b> (nó chốt tên job, lịch và cơ chế, không chốt cấu
 * hình Spring), nên tiền tố {@code catcheck.jobs.*} là quy ước của repo này, đặt theo đúng
 * khuôn {@code catcheck.*} mà {@code catcheck.i18n} / {@code catcheck.storage} đã dùng. Giá trị
 * mặc định ở đây <b>trích nguyên</b> cột "Lịch chạy" và {@code lockAtMostFor} của p12 §12.6.2 /
 * §12.6.6 — sửa giá trị là sửa spec, không phải sửa cấu hình.</p>
 *
 * <p>Các khoá này được đọc ở HAI nơi và phải khớp nhau: placeholder trong {@code @Scheduled} /
 * {@code @SchedulerLock} của từng job, và record này (để {@code JobHeartbeatCheckJob} biết lịch
 * kỳ vọng của job khác). Vì cả hai đọc cùng một khoá nên không có nguy cơ lệch.</p>
 *
 * @param enabled               công tắc tổng — tắt là mọi job dừng ghi {@code job_run} và không
 *                              làm gì; dùng cho môi trường chỉ phục vụ request
 * @param zone                  múi giờ của mọi cron (p12 §12.6.1 quy tắc 1: giờ ICT)
 * @param expireCreditBatches   p12 §12.6.2 — mỗi giờ phút :05
 * @param creditExpiringReminder p12 §12.6.2 — mỗi giờ phút :15
 * @param jobHeartbeatCheck     p12 §12.6.6 — mỗi 10 phút
 * @param sendDueReminders      p12 §12.6.3 — mỗi 5 phút
 * @param sendEmailOutbox       p12 §12.6.3 — mỗi 30 giây
 * @param retryFailedNotifications p12 §12.6.3 — mỗi 15 phút
 * @param cleanupDeadPushTokens p12 §12.6.3 — Chủ Nhật 04:00
 */
@ConfigurationProperties(prefix = "catcheck.jobs")
public record JobProperties(

        @DefaultValue("true") boolean enabled,
        @DefaultValue("Asia/Ho_Chi_Minh") ZoneId zone,
        @DefaultValue JobSetting expireCreditBatches,
        @DefaultValue JobSetting creditExpiringReminder,
        @DefaultValue JobSetting jobHeartbeatCheck,
        @DefaultValue JobSetting sendDueReminders,
        @DefaultValue JobSetting sendEmailOutbox,
        @DefaultValue JobSetting retryFailedNotifications,
        @DefaultValue JobSetting cleanupDeadPushTokens,
        @DefaultValue JobSetting dsarExportCleanup
) {

    /** Tên job ở {@code job_run.job_name} — đúng tên p12 §12.6.2, không viết tắt. */
    public static final String EXPIRE_CREDIT_BATCHES = "ExpireCreditBatchesJob";

    /** Tên job ở {@code job_run.job_name} — đúng tên p12 §12.6.2, không viết tắt. */
    public static final String CREDIT_EXPIRING_REMINDER = "CreditExpiringReminderJob";

    /** Tên job ở {@code job_run.job_name} — đúng tên p12 §12.6.6, không viết tắt. */
    public static final String JOB_HEARTBEAT_CHECK = "JobHeartbeatCheckJob";

    /** Tên job ở {@code job_run.job_name} — đúng tên p12 §12.6.3, không viết tắt. */
    public static final String SEND_DUE_REMINDERS = "SendDueRemindersJob";

    /** Tên job ở {@code job_run.job_name} — đúng tên p12 §12.6.3, không viết tắt. */
    public static final String SEND_EMAIL_OUTBOX = "SendEmailOutboxJob";

    /** Tên job ở {@code job_run.job_name} — đúng tên p12 §12.6.3, không viết tắt. */
    public static final String RETRY_FAILED_NOTIFICATIONS = "RetryFailedNotificationsJob";

    /** Tên job ở {@code job_run.job_name} — đúng tên p12 §12.6.3, không viết tắt. */
    public static final String CLEANUP_DEAD_PUSH_TOKENS = "CleanupDeadPushTokensJob";
    public static final String DSAR_EXPORT_CLEANUP = "DsarExportCleanupJob";

    /**
     * Danh mục job đã đăng ký ở bản build này, dạng máy đọc được.
     *
     * <p>p12 §12.6 liệt 29 job; hiện thực tới W2-B là 7 job. Danh sách này cố ý chỉ chứa job
     * CÓ THẬT trong code — {@code JobHeartbeatCheckJob} đếm "job quá hạn" dựa trên nó, nên kê
     * khai job chưa viết sẽ làm metric {@code catcheck.job.overdue_count} báo động giả vĩnh
     * viễn.</p>
     */
    public List<JobDescriptor> scheduledJobs() {
        return List.of(
                new JobDescriptor(EXPIRE_CREDIT_BATCHES, expireCreditBatches.cron(), zone,
                        enabled && expireCreditBatches.enabled()),
                new JobDescriptor(CREDIT_EXPIRING_REMINDER, creditExpiringReminder.cron(), zone,
                        enabled && creditExpiringReminder.enabled()),
                new JobDescriptor(JOB_HEARTBEAT_CHECK, jobHeartbeatCheck.cron(), zone,
                        enabled && jobHeartbeatCheck.enabled()),
                new JobDescriptor(SEND_DUE_REMINDERS, sendDueReminders.cron(), zone,
                        enabled && sendDueReminders.enabled()),
                new JobDescriptor(SEND_EMAIL_OUTBOX, sendEmailOutbox.cron(), zone,
                        enabled && sendEmailOutbox.enabled()),
                new JobDescriptor(RETRY_FAILED_NOTIFICATIONS, retryFailedNotifications.cron(), zone,
                        enabled && retryFailedNotifications.enabled()),
                new JobDescriptor(CLEANUP_DEAD_PUSH_TOKENS, cleanupDeadPushTokens.cron(), zone,
                        enabled && cleanupDeadPushTokens.enabled()),
                new JobDescriptor(DSAR_EXPORT_CLEANUP, dsarExportCleanup.cron(), zone,
                        enabled && dsarExportCleanup.enabled()));
    }

    /**
     * Cấu hình của một job.
     *
     * @param enabled       có đăng ký job này không
     * @param cron          cron 6 trường, giờ theo {@link JobProperties#zone()}
     * @param lockAtMostFor {@code @SchedulerLock(lockAtMostFor)} — p12 §12.7: đặt dài hơn thời
     *                      gian chạy tối đa dự kiến, không đặt sát
     * @param batchSize     số dòng lấy mỗi vòng {@code LIMIT} (p12 §12.6.1 quy tắc 3a)
     * @param maxItemsPerRun trần số item xử lý trong một lần chạy, để thời gian chạy dự đoán
     *                      được và job tự chia nhỏ qua nhiều lần gọi (p12 §12.7)
     * @param dryRun        chỉ đếm, không ghi/không xoá (p15 REQ-RET-01)
     */
    public record JobSetting(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("0 0 * * * *") String cron,
            @DefaultValue("PT50M") Duration lockAtMostFor,
            @DefaultValue("500") int batchSize,
            @DefaultValue("5000") int maxItemsPerRun,
            @DefaultValue("false") boolean dryRun
    ) {

        public JobSetting {
            if (batchSize <= 0) {
                throw new IllegalArgumentException("catcheck.jobs.*.batch-size phải > 0: " + batchSize);
            }
            if (maxItemsPerRun <= 0) {
                throw new IllegalArgumentException(
                        "catcheck.jobs.*.max-items-per-run phải > 0: " + maxItemsPerRun);
            }
        }
    }
}
