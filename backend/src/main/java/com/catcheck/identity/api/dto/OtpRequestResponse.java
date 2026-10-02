package com.catcheck.identity.api.dto;

import java.time.Instant;

/** A2 — kết quả yêu cầu gửi OTP (p8 §8.4.4 nhóm A). */
public record OtpRequestResponse(
        Instant otpExpiresAt,
        long canResendInSeconds,
        String maskedEmail) {
}
