package com.catcheck.privacy.domain;

/**
 * Kênh người dùng nộp yêu cầu DSAR (p4 §4.4.3 nhóm B, p15 REQ-DSAR-01).
 */
public enum DsarChannel {
    /** Tự thao tác trong app — vẫn tạo {@code dsar_request} (p15 REQ-DSAR-01). */
    SELF_SERVICE,
    /** Form trên web. */
    WEB_FORM,
    /** Gửi email. */
    EMAIL,
    /** Gửi văn bản giấy. */
    POST
}
