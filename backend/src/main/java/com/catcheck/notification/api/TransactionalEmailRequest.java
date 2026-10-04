package com.catcheck.notification.api;

import java.util.Map;

/**
 * Email giao dịch/bảo mật gửi tới một địa chỉ, <b>không cần user đã tồn tại</b> — đúng lý do
 * p4 F5 cố ý không đặt FK từ {@code email_outbox.to_address} sang {@code app_user}: OTP đăng ký
 * được gửi trước khi tài khoản tồn tại.
 *
 * <p>Những email này thuộc nhóm "không tắt được" (p12 §12.4.1, căn cứ {@code TT}) nên không đi
 * qua preference gate hay consent gate — nhưng đổi lại chỉ được chứa đúng nội dung mà căn cứ
 * pháp lý của nó cho phép.</p>
 */
public record TransactionalEmailRequest(
        String toAddress,
        String templateCode,
        String locale,
        Map<String, Object> payload,
        String dedupeKey) {

    public TransactionalEmailRequest {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
        locale = (locale == null || locale.isBlank()) ? "vi" : locale;
    }
}
