package com.catcheck.identity.domain;

/**
 * Trang thai yeu cau reset TOTP. Khop {@code user_mfa_reset_request.status}
 * (p4 §4.4.2, CHECK {@code ck_mfa_reset_status}).
 */
public enum MfaResetRequestStatus {

    /** Cho duyet. Chi ton tai toi da 1 yeu cau PENDING cho moi tai khoan (unique index cuc bo). */
    PENDING,

    APPROVED,

    /** {@code reject_reason} bat buoc khong null. */
    REJECTED,

    /** Het {@code expires_at}, job chuyen sang trang thai nay. */
    EXPIRED,

    /** Chinh chu so huu tai khoan rut lai. */
    CANCELLED
}
