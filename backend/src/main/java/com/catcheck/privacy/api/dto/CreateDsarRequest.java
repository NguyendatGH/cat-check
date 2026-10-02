package com.catcheck.privacy.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Body C13 — DSAR không tự phục vụ được: {@code RECTIFY}, {@code OBJECT},
 * {@code PROTECTION_MEASURE}, {@code COMPLAINT} (p8 §8.4.3).
 *
 * @param requestType loại yêu cầu — ngoài danh sách ⇒ 400 DSAR_REQUEST_TYPE_UNSUPPORTED
 * @param channel     kênh nộp — mặc định SELF_SERVICE khi client không gửi
 */
public record CreateDsarRequest(
        @NotBlank(message = "requestType không được trống")
        @Pattern(regexp = "[A-Z_]{1,24}", message = "requestType không hợp lệ")
        String requestType,

        @Pattern(regexp = "[A-Z_]{1,16}", message = "channel không hợp lệ")
        String channel
) {
}
