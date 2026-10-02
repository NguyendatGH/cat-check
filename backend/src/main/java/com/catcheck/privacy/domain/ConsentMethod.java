package com.catcheck.privacy.domain;

/**
 * Kênh ghi nhận sự đồng ý (p4 §4.4.3 nhóm B) — bằng chứng "cách thức xin đồng ý phải kiểm
 * chứng được" theo Điều 6.1 NĐ356.
 */
public enum ConsentMethod {
    /** Tick checkbox lúc đăng ký (màn đăng ký / Google OAuth bước cuối). */
    WEB_CHECKBOX,
    /** Bật/tắt ở Trung tâm quyền riêng tư. */
    WEB_TOGGLE,
    /** Xác nhận qua link trong email. */
    EMAIL_LINK,
    /** DPO ghi thay người dùng — bắt buộc có {@code dsar_request} liên quan. */
    ADMIN_ON_BEHALF,
    /** Di trú dữ liệu từ hệ thống cũ. */
    IMPORT
}
