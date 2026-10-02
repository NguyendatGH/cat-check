package com.catcheck.cat.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * D2 — body tạo hồ sơ mèo.
 *
 * <p>Độ dài {@code name} khớp p4 {@code cat.name VARCHAR(60)}. Cân nặng theo p4 {@code 0 < weight_kg < 30}
 * (khác với ví dụ 0.1–25 trong phần test của p8; chốt theo p4 là chuẩn thứ nhất).</p>
 */
public record CreateCatRequest(
        @NotBlank(message = "Vui lòng nhập tên bé mèo")
        @Size(max = 60, message = "Tên bé mèo tối đa 60 ký tự")
        String name,
        LocalDate birthDate,
        Integer approxAgeMonths,
        String breedCode,
        String breedOther,
        String coatColor,
        String sex,
        Boolean neutered,
        @DecimalMin(value = "0.01", message = "Cân nặng phải lớn hơn 0")
        @DecimalMax(value = "29.99", message = "Cân nặng phải nhỏ hơn 30")
        BigDecimal weightKg,
        Boolean isPrimary,
        @Size(max = 2000, message = "Ghi chú tối đa 2000 ký tự")
        String notes
) {
}
