package com.catcheck.identity.api.dto;

import jakarta.validation.constraints.NotBlank;

public record OtpRequestRequest(
        String email,
        @NotBlank String purpose,
        String registrationToken) {
}
