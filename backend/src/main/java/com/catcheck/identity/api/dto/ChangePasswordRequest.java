package com.catcheck.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * B6 — đổi mật khẩu khi đã đăng nhập (p8 §8.4.4 nhóm B: {@code POST /account/password}).
 *
 * <p>{@code currentPassword} chính là bước step-up {@code PASSWORD} của endpoint này — không
 * cần gọi {@code POST /auth/reauth} trước (p8 §8.4.2 B6: {@code U!} + {@code S1}).</p>
 */
public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 8, max = 72) String newPassword) {
}
