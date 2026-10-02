package com.catcheck.privacy.domain;

/**
 * Phương thức xác minh danh tính trước khi xuất/xoá dữ liệu (p4 §4.4.3 nhóm B,
 * p15 REQ-DSAR-04): user đang đăng nhập → xác nhận lại bằng OTP email; user không
 * đăng nhập được → quy trình thủ công do DPO xử lý.
 */
public enum IdentityMethod {
    /** Đang đăng nhập (phiên hợp lệ). */
    SESSION,
    /** Xác minh bằng mã OTP gửi tới email. */
    EMAIL_OTP,
    /** DPO xử lý thủ công (giấy tờ tùy thân). */
    ID_DOC_MANUAL
}
