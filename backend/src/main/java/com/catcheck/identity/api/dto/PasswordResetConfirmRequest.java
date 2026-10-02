package com.catcheck.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A10 — xác nhận đặt lại mật khẩu (p8 §8.4.4 nhóm A). */
public record PasswordResetConfirmRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8, max = 72) String newPassword) {
}
