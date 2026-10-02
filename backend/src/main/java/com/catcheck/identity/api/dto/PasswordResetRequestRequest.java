package com.catcheck.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** A9 — yêu cầu đặt lại mật khẩu (p8 §8.4.4 nhóm A). */
public record PasswordResetRequestRequest(
        @Email @NotBlank String email) {
}
