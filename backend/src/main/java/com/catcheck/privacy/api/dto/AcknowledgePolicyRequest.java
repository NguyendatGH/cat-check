package com.catcheck.privacy.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Body C17 — ghi nhận "đã đọc và hiểu" một văn bản pháp lý (p8 §8.4.3). Đây là
 * <b>acknowledgment</b>, KHÔNG phải consent dữ liệu cá nhân (p4 B4).
 *
 * @param surface điểm chạm: onboarding_disclaimer / result_screen_footer / terms_register
 */
public record AcknowledgePolicyRequest(
        @NotBlank(message = "surface không được trống")
        @Pattern(regexp = "[a-z_]{1,64}", message = "surface không hợp lệ")
        String surface
) {
}
