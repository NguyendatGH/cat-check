package com.catcheck.privacy.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body chung cho các hành động admin chỉ cần ký hiệu {@code Rsn} của p8 §8.3.2 (L52, L53).
 *
 * <p>Không dùng kiểu nguyên thuỷ (H15.99): với {@code record}, Jackson gọi canonical
 * constructor và truyền {@code null} cho mọi property vắng mặt, nên một {@code boolean}
 * hay {@code int} ở đây biến "thiếu field" thành {@code 500} thay vì {@code 400}.</p>
 *
 * @param reason lý do ≥ 10 ký tự (p15 REQ-AUD-03); thiếu ⇒ {@code 400 REASON_REQUIRED}
 */
public record AdminReasonRequest(
        @NotBlank @Size(max = 2000) String reason) {
}
