package com.catcheck.identity.api.dto;

import jakarta.validation.constraints.NotBlank;

/** A12 — đăng nhập bằng recovery code (p8 §8.4.4 nhóm A). */
public record TotpRecoveryRequest(
        @NotBlank String recoveryCode) {
}
