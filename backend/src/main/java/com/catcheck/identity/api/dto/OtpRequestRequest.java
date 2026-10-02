package com.catcheck.identity.api.dto;

import jakarta.validation.constraints.NotBlank;

/** A2 — yêu cầu gửi OTP (p8 §8.4.4 nhóm A). */
public record OtpRequestRequest(
        String email,
        @NotBlank String purpose,
        String registrationToken) {
}
