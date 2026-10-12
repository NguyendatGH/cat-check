package com.catcheck.identity.domain;

import java.time.Duration;

public record AuthPolicy(
        Duration otpTtl,
        int otpMaxAttempts,
        Duration otpTicketTtl,
        int otpCodeLength,

        int bcryptStrength,
        int passwordMinLength,
        int passwordMaxLength,

        int lockoutThresholdShort,
        Duration lockoutDurationShort,
        int lockoutThresholdLong,
        Duration lockoutDurationLong,

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
