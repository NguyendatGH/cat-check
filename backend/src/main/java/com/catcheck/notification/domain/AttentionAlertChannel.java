package com.catcheck.notification.domain;

/**
 * {@code user_notification_preference.attention_alert_channel} (p4 F4).
 *
 * <p><b>Cố ý KHÔNG có giá trị "tắt"</b> — cảnh báo kết quả cần chú ý là lý do tồn tại của sản
 * phẩm (quyết định #10); user chỉ được chọn kênh.</p>
 *
 * <p>Trùng tên với {@code identity.domain.AttentionAlertChannel} là CÓ CHỦ Ý: hai module không
 * được import type miền của nhau (Modulith R6). identity sở hữu luồng GHI (p8 B11
 * {@code PUT /account/notification-preferences}); notification chỉ ĐỌC để quyết định kênh.</p>
 */
public enum AttentionAlertChannel {
    PUSH_AND_INAPP,
    INAPP_ONLY
}
