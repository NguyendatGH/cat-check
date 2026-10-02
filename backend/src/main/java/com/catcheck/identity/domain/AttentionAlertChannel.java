package com.catcheck.identity.domain;

/**
 * Kênh nhận cảnh báo "kết quả cần chú ý" — cột
 * {@code user_notification_preference.attention_alert_channel} (p4 F4, V13).
 *
 * <p><b>Cố ý KHÔNG có giá trị "tắt".</b> Rule cảnh báo là lý do tồn tại của sản phẩm (quyết
 * định #10), nên p4 F4 chỉ cho user chọn kênh chứ không cho tắt hẳn. Bất biến này vì thế là
 * bất biến KIỂU — không cần kiểm ở tầng service, và CHECK {@code ck_unp_attention_channel}
 * của V13 ép đúng hai giá trị này ở tầng DB.</p>
 */
public enum AttentionAlertChannel {
    /** Đẩy push + hiện trong app. */
    PUSH_AND_INAPP,
    /** Chỉ hiện trong app — mức tối thiểu, không thể thấp hơn. */
    INAPP_ONLY
}
