package com.catcheck.colorchart.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body của L33 ({@code POST .../backfill-preview}).
 *
 * <p>Không dùng kiểu nguyên thuỷ (handoff H15.99): với {@code record}, Jackson truyền
 * {@code null} cho mọi property vắng mặt và {@code FAIL_ON_NULL_FOR_PRIMITIVES} ném ngay ở bước
 * bind ⇒ {@code 500} thay vì {@code 400}, và tệ hơn là ném TRƯỚC khi controller kịp kiểm
 * {@code If-Match}/{@code reason}.</p>
 *
 * @param window cửa sổ thời gian ISO-8601 ({@code P90D}, {@code P6M}); vắng ⇒ mặc định
 *               {@code P90D} (p6 §6.5.4)
 * @param reason ký hiệu {@code Rsn} của p8 §8.4.12 — bắt buộc ≥ 10 ký tự (p15 REQ-AUD-03)
 */
public record StartBackfillPreviewRequest(
        @Pattern(regexp = "^[Pp](\\d+[Yy])?(\\d+[Mm])?(\\d+[Ww])?(\\d+[Dd])?$",
                message = "window phải là chu kỳ ISO-8601, ví dụ P90D")
        String window,

        @NotBlank
        @Size(min = 10, max = 500)
        String reason
) {
}
