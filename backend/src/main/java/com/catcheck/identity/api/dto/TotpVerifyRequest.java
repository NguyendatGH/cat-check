package com.catcheck.identity.api.dto;

import jakarta.validation.constraints.NotBlank;

/** A11 / B15 — xác thực mã TOTP (p8 §8.4.4 nhóm A/B). */
public record TotpVerifyRequest(
        @NotBlank String code) {
}
