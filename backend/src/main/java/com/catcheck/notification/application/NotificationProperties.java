package com.catcheck.notification.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình module notification. Tên biến môi trường lấy theo p18 §18.9 (p18 sở hữu miền biến
 * môi trường — không tự đặt tên mới).
 *
 * <p>Đặt ở {@code application} chứ không {@code infrastructure} vì ArchUnit R2 cấm
 * {@code ..application..} phụ thuộc {@code ..infrastructure..} của chính module mình, mà service
 * ở tầng application cần đọc trần thiết bị. Bean được nạp bởi
 * {@code infrastructure.NotificationConfiguration} (chiều infrastructure → application là hợp
 * lệ).</p>
 */
@ConfigurationProperties(prefix = "catcheck.notification")
public record NotificationProperties(Push push, Outbox outbox) {

    public NotificationProperties {
        push = push == null ? new Push(null, null, null) : push;
        outbox = outbox == null ? new Outbox(null, null, null) : outbox;
    }

    /**
     * @param serviceAccountPath {@code FIREBASE_SERVICE_ACCOUNT_PATH} — <b>đường dẫn</b> tới file
     *                           service account JSON được mount read-only. research §15.3 cấm
     *                           nhét cả nội dung JSON vào env var. Rỗng ⇒ push bị vô hiệu hoá có
     *                           kiểm soát, app vẫn khởi động
     * @param maxDevicesPerUser  trần 10 thiết bị/user ({@code push.max_subscriptions_per_user},
     *                           p4 F3 + p8 {@code PUSH_SUBSCRIPTION_LIMIT})
     */
    public record Push(String serviceAccountPath, String projectId, Integer maxDevicesPerUser) {

        public Push {
            maxDevicesPerUser = maxDevicesPerUser == null ? 10 : maxDevicesPerUser;
        }

        public boolean configured() {
            return serviceAccountPath != null && !serviceAccountPath.isBlank();
        }
    }

    /**
     * @param maxAttempts tối đa 5 lần thử rồi vào dead letter (p12 §12.8.1)
     *
     * <p><b>Hai công tắc {@code email-job-enabled}/{@code push-job-enabled} đã bị bỏ ở W2-B
     * (H15.73).</b> Chúng từng sống ở đây vì {@code shared.job.JobProperties} là một record
     * ĐÓNG thuộc gói việc khác; nay hai job outbox chạy trên nền {@code shared/job} nên công
     * tắc nằm đúng chỗ của mọi job khác: {@code catcheck.jobs.send-email-outbox.enabled} và
     * {@code catcheck.jobs.retry-failed-notifications.enabled}. Giữ lại ở hai nơi sẽ sinh ra
     * câu hỏi "tắt ở đâu mới thật sự tắt".</p>
     */
    public record Outbox(Integer maxAttempts, Integer emailBatchSize, Integer pushBatchSize) {

        public Outbox {
            maxAttempts = maxAttempts == null ? 5 : maxAttempts;
            emailBatchSize = emailBatchSize == null ? 50 : emailBatchSize;
            pushBatchSize = pushBatchSize == null ? 100 : pushBatchSize;
        }
    }
}
