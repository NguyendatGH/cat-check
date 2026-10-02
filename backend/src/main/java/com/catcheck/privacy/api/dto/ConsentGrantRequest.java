package com.catcheck.privacy.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Một mục khai báo đồng ý — dùng chung body đăng ký (p8 §8.5.1) và C3.
 *
 * @param purposeCode mã mục đích, phải tồn tại trong {@code consent_purpose}
 * @param granted     true = cấp, false = rút/từ chối
 */
public record ConsentGrantRequest(
        @NotBlank(message = "purposeCode không được trống")
        @Pattern(regexp = "[A-Z_]{1,48}", message = "purposeCode không hợp lệ")
        String purposeCode,
        boolean granted
) {
}
