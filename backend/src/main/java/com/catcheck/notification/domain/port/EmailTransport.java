package com.catcheck.notification.domain.port;

import com.catcheck.notification.domain.OutgoingEmail;

/**
 * Giao email cho hạ tầng (SMTP, hoặc ghi file ở máy dev).
 *
 * <p>Khác {@code notification.api.EmailSender} ở đúng một điểm và đó là điểm quan trọng:
 * {@code deliver} <b>NÉM</b> khi thất bại. {@code EmailSender.send} nuốt lỗi (hợp đồng cũ:
 * SMTP treo không được làm hỏng luồng đăng ký), nhưng job outbox thì BẮT BUỘC phải biết lần
 * gửi có thành công hay không — nếu không thì backoff/dead-letter của p12 §12.8.1 vô nghĩa.</p>
 */
public interface EmailTransport {

    /**
     * @throws RuntimeException khi không gửi được — job bắt, tăng {@code attempts}, đặt
     *                          {@code next_attempt_at} theo backoff
     */
    void deliver(OutgoingEmail email);
}
