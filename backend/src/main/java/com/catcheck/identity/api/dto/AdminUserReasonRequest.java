package com.catcheck.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body chỉ có {@code reason} — L3, L7, L8 (ký hiệu {@code Rsn} của p8 §8.3.2).
 *
 * <p>{@code @Size(min = 10)} khớp {@code AdminGuard.REASON_MIN_LENGTH}; cả hai cùng đến từ
 * p15 REQ-AUD-03. Bean validation trả {@code 400 VALIDATION_FAILED} với trường cụ thể (tốt cho
 * form), {@code AdminGuard} trả {@code 400 REASON_REQUIRED} (mã mà p8 đặt tên) cho các đường
 * vào không qua bean validation.</p>
 */
public record AdminUserReasonRequest(@NotBlank @Size(min = 10, max = 500) String reason) {
}
