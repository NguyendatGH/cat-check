package com.catcheck.privacy.domain;

/**
 * Điểm chạm hiển thị văn bản pháp lý lúc ghi {@code policy_acknowledgment}
 * (p4 §4.4.3 nhóm B). Lưu ý: đây là <b>acknowledgment</b> (đã đọc), KHÔNG phải
 * consent dữ liệu cá nhân — không được trộn vào {@code consent_record} (p4 B4).
 */
public enum PolicySurface {
    /** Disclaimer y tế ở bước onboarding (p15 §15.3.3). */
    onboarding_disclaimer,
    /** Chân trang màn hình kết quả quét. */
    result_screen_footer,
    /** Điều khoản ở màn đăng ký. */
    terms_register
}
