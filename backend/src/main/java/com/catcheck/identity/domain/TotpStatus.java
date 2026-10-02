package com.catcheck.identity.domain;

/**
 * Trang thai TOTP. Khop {@code user_mfa_totp.status}
 * (p4 §4.4.2, CHECK {@code ck_totp_status}).
 */
public enum TotpStatus {

    /** Da sinh secret nhung chua xac nh bang ma dung; {@code pending_expires_at} bat buoc khong null. */
    PENDING,

    /** Da kich hoat, secret duoc dung de xac thuc. */
    ACTIVE
}
