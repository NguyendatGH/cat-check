package com.catcheck.identity.api.dto;

import jakarta.validation.constraints.NotBlank;

/** A3 — xác thực OTP (p8 §8.4.4 nhóm A). */
public record OtpVerifyRequest(
        String email,
        @NotBlank String purpose,
        @NotBlank String code,
        String registrationToken) {
}
