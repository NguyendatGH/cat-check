package com.catcheck.identity.api.dto;

import jakarta.validation.constraints.NotBlank;

public record OtpVerifyRequest(
        String email,
        @NotBlank String purpose,
        @NotBlank String code,
        String registrationToken) {
}
