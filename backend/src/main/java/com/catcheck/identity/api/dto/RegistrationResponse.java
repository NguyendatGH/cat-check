package com.catcheck.identity.api.dto;

import java.time.Instant;
import java.util.UUID;

public record RegistrationResponse(
        UUID registrationToken,
        Instant otpExpiresAt,
        long canResendInSeconds) {
}
