package com.catcheck.notification.api;

import java.util.UUID;

/**
 * Cổng để module nghiệp vụ phát thông báo. Hiện thực là
 * {@code notification.application.NotificationService}.
 *
 * <p><b>Hợp đồng:</b> cả hai phương thức chỉ {@code INSERT} (notification + outbox) và trở về
 * ngay — KHÔNG gọi SMTP/FCM trong request thread (p12 §12.4, §12.8). Gọi trong cùng
 * transaction với sự kiện nghiệp vụ để không có trường hợp "việc đã xảy ra mà thông báo biến
 * mất" (hoặc ngược lại).</p>
 */
public interface NotificationGateway {

    /**
     * Phát một thông báo. Luôn ghi bản ghi in-app cho mọi template không-marketing (p12 §12.1:
     * in-app là nguồn sự thật, push/email chỉ là "đẩy" thêm); push/email chỉ được thêm khi
     * preference + consent + giờ im lặng cho phép.
     */
    void enqueue(NotificationRequest request);

    /** Đưa một email giao dịch vào {@code email_outbox}. */
    void enqueueTransactionalEmail(TransactionalEmailRequest request);

    /**
     * Thu hồi MỌI đăng ký push của một user vì họ vừa rút consent
     * {@code HEALTH_REMINDER_PUSH} (p12 §12.3.9, p15 §15.3.5 bảng "tác động khi rút").
     *
     * <p><b>Ngay lập tức, không chờ {@code CleanupDeadPushTokensJob}.</b> Consent là căn cứ
     * pháp lý để gửi push; còn một dòng {@code push_subscription} chưa thu hồi là còn một
     * đường để một thông báo lọt ra sau khi người dùng đã nói "không" — và lần gửi đó không
     * có căn cứ nào cả. Job dọn hằng tuần chỉ xoá bản ghi đã thu hồi quá 30 ngày; nó là cơ
     * chế retention, không phải cơ chế tuân thủ.</p>
     *
     * <p>Chỉ đặt {@code revoked_at} + {@code revoke_reason = 'CONSENT_WITHDRAWN'}, KHÔNG xoá
     * dòng: bản ghi thu hồi chính là bằng chứng đã thực thi quyền rút (p15 §15.10-C). Gọi
     * trong cùng transaction với việc ghi dòng {@code WITHDRAWN} vào {@code consent_record},
     * để không có trạng thái "đã ghi rút mà thiết bị vẫn nhận".</p>
     *
     * @param userId chủ thể dữ liệu vừa rút consent
     * @return số đăng ký vừa bị thu hồi (0 là bình thường: user chưa bật push bao giờ)
     */
    int revokePushOnConsentWithdrawal(UUID userId);
}
