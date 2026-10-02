package com.catcheck.colorchart.api.dto;

import com.catcheck.colorchart.domain.ProductLine;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body của L28 — tạo bảng màu {@code DRAFT}.
 *
 * <p>Không có trường nào cho phép đặt {@code status} thành {@code ACTIVE}: trạng thái chỉ đổi qua
 * L32 (publish), để không có đường nào publish mà bỏ qua validation.
 */
public record CreateColorChartRequest(
        @NotBlank
        @Size(max = 64)
        String code,

        @NotBlank
        @Size(max = 200)
        String name,

        @NotNull
        ProductLine productLine,

        @Size(max = 64)
        String productionBatch
) {
}
