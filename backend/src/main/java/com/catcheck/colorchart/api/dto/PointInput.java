package com.catcheck.colorchart.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Một mức pH trong body L31 ({@code PUT /admin/color-charts/{chartId}/points}).
 *
 * <p>Admin nhập {@code hexSrgb} (luồng 1) hoặc {@code labL/labA/labB} trực tiếp (luồng 2). Khi
 * nhập hex, server tự tính Lab bằng {@code ColorSpace.hexToLab} (p6 §6.6.3).
 */
public record PointInput(
        @NotNull
        @DecimalMin("0.0")
        @DecimalMax("14.0")
        BigDecimal phValue,

        @NotNull
        @DecimalMin("0.0")
        @DecimalMax("100.0")
        BigDecimal labL,

        @NotNull
        BigDecimal labA,

        @NotNull
        BigDecimal labB,

        @NotNull
        @DecimalMin("0.01")
        BigDecimal toleranceDeltaE,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "hexSrgb phải là #RRGGBB")
        String hexSrgb,

        @NotBlank
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "displayHex phải là #RRGGBB")
        String displayHex,

        @NotBlank
        @Size(max = 80)
        String displayNameVi,

        @Size(max = 80)
        String displayNameEn
) {
}
