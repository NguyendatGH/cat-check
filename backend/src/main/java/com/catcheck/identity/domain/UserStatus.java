package com.catcheck.identity.domain;

/**
 * Trang thai tai khoan. Gia tri phai khop chinh xac {@code app_user.status}
 * (p4 §4.4.2 va CHECK {@code ck_app_user_status}).
 */
public enum UserStatus {

    /** Da tao, chua xac minh email. Chua duoc phep xac thuc tai khoan. */
    PENDING_VERIFICATION,

    /** Da xac minh email, dung duoc het chuc nang. */
    ACTIVE,

    /** Khoa do doi mat khau sai qua nhieu lan. Cho phep mo khoa bang OTP. */
    LOCKED,

    /** Van dung duoc, nhung khong duoc xu ly du lieu (chi doc, cam hanh dong phat sinh du lieu). */
    RESTRICTED,

    /** Da xin xoa, con cho {@code deletion_scheduled_at} het han. */
    DELETION_REQUESTED,

    /** Da an danh hoan toan, {@code pseudonym_id} giu du lieu tham khoa. */
    ANONYMIZED
}
