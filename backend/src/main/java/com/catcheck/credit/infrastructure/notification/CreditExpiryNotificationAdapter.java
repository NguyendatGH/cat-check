package com.catcheck.credit.infrastructure.notification;

import com.catcheck.credit.application.spi.CreditExpiryNotificationPort;
import com.catcheck.credit.domain.ExpiryReminderMilestone;
import com.catcheck.notification.api.NotificationGateway;
import com.catcheck.notification.api.NotificationRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Nối {@link CreditExpiryNotificationPort} vào {@code notification::api} — H15.46.
 *
 * <p>Đây là mảnh còn thiếu khiến {@code CreditExpiringReminderJob} phải tắt suốt từ W1-B:
 * {@code CreditExpiryReminderService} đã có đủ phần quét/khoá/đánh cờ, nhưng không có ai hiện
 * thực cổng ra nên job bật lên chỉ sinh một dòng {@code job_run} {@code FAILED} mỗi giờ. Với
 * adapter này, {@code catcheck.jobs.credit-expiring-reminder.enabled} đã được bật lại.</p>
 *
 * <p><b>Chỉ xếp hàng, không gửi</b> (p12 §12.6.1 quy tắc 8): {@code NotificationGateway.enqueue}
 * chỉ {@code INSERT} vào {@code notification}/{@code notification_outbox}/{@code email_outbox}
 * rồi trả về, và nó chạy trong transaction {@code REQUIRES_NEW} mà
 * {@code CreditExpiryReminderService.queueReminder} đã mở — nên bản ghi thông báo và cờ
 * {@code t48h_notified_at}/{@code t6h_notified_at} commit cùng lúc.</p>
 *
 * <p><b>Vì sao adapter nằm ở {@code credit.infrastructure} chứ không ở {@code notification}:</b>
 * cổng là của {@code credit} (nó định nghĩa hợp đồng mình cần), nên hiện thực thuộc vành ngoài
 * của chính {@code credit} — đúng khuôn hexagonal mà mọi adapter khác của module này dùng.
 * Chiều phụ thuộc {@code credit} → {@code notification::api} đã được khai ở
 * {@code credit/package-info.java}; chiều ngược lại không tồn tại nên không có chu trình.</p>
 */
@Component
class CreditExpiryNotificationAdapter implements CreditExpiryNotificationPort {

    /** {@code notification.ref_type} — truyền bằng chuỗi để không import enum của module kia. */
    private static final String REF_TYPE_CREDIT = "CREDIT";

    /** Route thật của p9 §9.4, đúng cột "Deep link" của p12 §12.2.2. */
    private static final String DEEP_LINK = "/credits";

    private final NotificationGateway notifications;

    CreditExpiryNotificationAdapter(NotificationGateway notifications) {
        this.notifications = notifications;
    }

    @Override
    public void queueExpiryReminder(
            UUID userId,
            UUID batchId,
            ExpiryReminderMilestone milestone,
            Instant expiresAt,
            int remainingAmount) {

        String templateCode = milestone.templateCode();
        notifications.enqueue(new NotificationRequest(
                userId,
                templateCode,
                Map.of(
                        "batchId", batchId.toString(),
                        "remaining", remainingAmount,
                        "expiresAt", expiresAt.toString(),
                        "milestone", milestone.name(),
                        "deepLink", DEEP_LINK),
                title(milestone),
                body(milestone, remainingAmount),
                REF_TYPE_CREDIT,
                batchId,
                // Quy ước p12 §12.4: <template>:<refType>:<refId>:<mốc>.
                templateCode + ":CREDIT:" + batchId + ':' + milestone.name()));
    }

    /** Nguyên văn cột "Tiêu đề (VI)" của p12 §12.2.2. */
    private static String title(ExpiryReminderMilestone milestone) {
        return milestone == ExpiryReminderMilestone.T48H
                ? "Credit sắp hết hạn"
                : "Credit sắp hết hạn — còn 6 giờ";
    }

    /**
     * Nguyên văn cột "Nội dung (VI)" của p12 §12.2.2, bỏ dấu {@code **} nhấn mạnh: cột đó viết
     * bằng markdown cho người đọc spec, còn {@code notification.body_snapshot} là văn bản thuần
     * hiển thị trên banner push/lock screen — in ra hai dấu sao là lỗi hiển thị, không phải
     * nhấn mạnh.
     */
    private static String body(ExpiryReminderMilestone milestone, int remainingAmount) {
        return milestone == ExpiryReminderMilestone.T48H
                ? "Bạn còn " + remainingAmount + " credit sắp hết hạn trong 2 ngày. "
                        + "Dùng ngay để không bị mất."
                : "Chỉ còn 6 giờ trước khi " + remainingAmount + " credit hết hạn.";
    }
}
