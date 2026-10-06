package com.catcheck.colorchart.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body của L35 ({@code POST .../backfill-apply}). Chỉ có {@code reason} — phạm vi áp dụng chính
 * là lượt preview đang lưu, nên không có tham số nào để chọn lại: nếu cho chọn cửa sổ khác ở
 * bước này thì con số admin vừa duyệt ở L34 không còn mô tả việc sắp xảy ra.
 *
 * @param reason ký hiệu {@code Rsn} của p8 §8.4.12 — bắt buộc ≥ 10 ký tự
 */
public record StartBackfillApplyRequest(
        @NotBlank
        @Size(min = 10, max = 500)
        String reason
) {
}
