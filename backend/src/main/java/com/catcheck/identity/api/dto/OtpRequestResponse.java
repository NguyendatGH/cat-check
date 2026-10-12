package com.catcheck.identity.api.dto;

import java.time.Instant;

public record OtpRequestResponse(
        Instant otpExpiresAt,
        long canResendInSeconds,
        String maskedEmail) {
}
