package com.catcheck.identity.domain;

import java.time.Duration;

/**
 * Tham so <b>chinh sach</b> xac thuc ma tang {@code application} doc duoc (p11 §11.2.1,
 * §11.3.1, §11.7.2, §11.1.3).
 *
 * <p>Tai sao khong doc thang {@code IdentityAuthProperties}: class do la
 * {@code @ConfigurationProperties} nam o {@code ..infrastructure.config..}, ma R2 cam
 * {@code ..application..} phu thuoc chinh infrastructure cua module. Ban ghi la mot
 * record thuan tuy Java trong domain; {@code ..infrastructure..} tao no tu
 * {@code IdentityAuthProperties} (xem {@code IdentityAuthPolicyConfiguration}).</p>
 *
 * <p>Moi truong day la <b>so lieu dac biet</b>, khong phai cau hinh: doi mot so o day la
 * doi hanh vi pham, nen khong dua vao bien moi truong rong.</p>
 */
public record AuthPolicy(
        /* OTP (p11 S5) */
        Duration otpTtl,
        int otpMaxAttempts,
        Duration otpTicketTtl,
        int otpCodeLength,

        /* Mat khau (p11 S4) */
        int bcryptStrength,
        int passwordMinLength,
        int passwordMaxLength,

        /* Khoa tai khoan do sai mat khau (p11 §11.7.2) */
        int lockoutThresholdShort,
        Duration lockoutDurationShort,
        int lockoutThresholdLong,
        Duration lockoutDurationLong,

        /* Phien (p11 §11.1.3) */
        Duration sessionMaxInactive,
        Duration sessionDefaultMaxInactive,
        int maxConcurrentSessions) {

    public AuthPolicy {
        if (otpTtl.isNegative() || otpTtl.isZero()) {
            throw new IllegalArgumentException("otpTtl phai duong");
        }
        if (otpMaxAttempts < 1) {
            throw new IllegalArgumentException("otpMaxAttempts phai >= 1");
        }
        if (otpTicketTtl.isNegative() || otpTicketTtl.isZero()) {
            throw new IllegalArgumentException("otpTicketTtl phai duong");
        }
        if (lockoutThresholdShort < 1 || lockoutThresholdLong < lockoutThresholdShort) {
            throw new IllegalArgumentException("nguong khoa tai khoan khong hop le");
        }
        if (maxConcurrentSessions < 1) {
            throw new IllegalArgumentException("maxConcurrentSessions phai >= 1");
        }
    }
}
