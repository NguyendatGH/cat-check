package com.catcheck.identity.api.dto;

import java.time.Instant;
import java.util.UUID;

/** A1 — kết quả đăng ký, chờ xác thực OTP (p8 §8.4.4 nhóm A). */
public record RegistrationResponse(
        UUID registrationToken,
        Instant otpExpiresAt,
        long canResendInSeconds) {
}
