package com.catcheck.identity.domain;

/**
 * Muc dich cua ma OTP trong {@code email_otp}.
 *
 * <p>Gia tri la hop nhat cua ba noi: p4 §4.4.2 + p8 §8.4.1 (A4) cho 4 gia tri
 * {@code REGISTER_VERIFY, EMAIL_CHANGE, LOGIN_STEPUP, DSAR_VERIFY}, va p11 §11.2.1/§11.2.6
 * yeu cau them {@code PASSWORD_RESET, ACCOUNT_DELETE_CONFIRM, DATA_EXPORT_CONFIRM}.
 * p11 la chu so huu mien xac thuc nen thang khi p4 §A3 con ghi 3 gia tri.</p>
 */
public enum OtpPurpose {

    /** Xac minh email luc dang ky — xac nhan {@code app_user.email_verified_at}. */
    REGISTER_VERIFY,

    /** Xac minh email moi khi doi email. */
    EMAIL_CHANGE,

    /** Xac thuc buoc hai khi dang nhap lai (re-auth cho hanh dong nhat). */
    LOGIN_STEPUP,

    /** Xac minh danh tinh de thuc hien yeu cau DSAR. */
    DSAR_VERIFY,

    /** Yeu cau dat lai mat khau. */
    PASSWORD_RESET,

    /** Xac nhan xoa tai khoan. */
    ACCOUNT_DELETE_CONFIRM,

    /** Xac nhan xuat du lieu ca nhan. */
    DATA_EXPORT_CONFIRM
}
