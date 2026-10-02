package com.catcheck.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** A6 — đăng nhập bằng mật khẩu (p8 §8.4.4 nhóm A). */
public record LoginRequest(
        @Email @NotBlank String email,
        @NotBlank String password,
        Boolean rememberMe) {
}
