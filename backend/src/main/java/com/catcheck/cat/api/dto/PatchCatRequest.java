package com.catcheck.cat.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * D4 — body sửa hồ sơ mèo (merge-patch, p8 §8.5.3). Mọi trường {@code null} nghĩa là giữ nguyên
 * (xem {@code Cat#update}) — KHÔNG dùng để đặt {@code isPrimary}, D8 là endpoint riêng.
 */
public record PatchCatRequest(
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
        @Size(max = 2000, message = "Ghi chú tối đa 2000 ký tự")
        String notes
) {
}
