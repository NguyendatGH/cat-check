package com.catcheck.credit.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body chỉ có {@code reason} — dùng cho L23/L24 (void mã, void lô).
 *
 * <p>Ký hiệu {@code Rsn} của p8 §8.3.2 + p15 REQ-AUD-03: thiếu hoặc dưới 10 ký tự ⇒
 * {@code 400}. Bean validation bắt trước, {@code AdminGuard.requireReason} là lớp thứ hai cho
 * các đường vào không qua bean validation (query param của L22, script nội bộ).</p>
 */
public record AdminReasonRequest(@NotBlank @Size(min = 10, max = 500) String reason) {
}
