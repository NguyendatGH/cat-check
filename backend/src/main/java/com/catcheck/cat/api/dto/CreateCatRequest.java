package com.catcheck.cat.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateCatRequest(
        @NotBlank(message = "{validation.cat.name.required}")
        @Size(max = 60, message = "{validation.cat.name.max}")
        String name,
        LocalDate birthDate,
        Integer approxAgeMonths,
        String breedCode,
        String breedOther,
        String coatColor,
        String sex,
        Boolean neutered,
        @DecimalMin(value = "0.01", message = "{validation.cat.weight.min}")
        @DecimalMax(value = "29.99", message = "{validation.cat.weight.max}")
        BigDecimal weightKg,
        Boolean isPrimary,
        @Size(max = 2000, message = "{validation.cat.note.max}")
        String notes
) {
}
