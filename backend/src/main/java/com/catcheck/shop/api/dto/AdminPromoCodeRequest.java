package com.catcheck.shop.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record AdminPromoCodeRequest(
        @NotBlank @Size(max = 50) @Pattern(regexp = "^[A-Za-z0-9_-]{2,50}$", message = "Code must be 2-50 alphanumeric characters") String code,
        @NotBlank @Size(max = 120) String label,
        @NotBlank @Pattern(regexp = "(?i)PERCENT|FIXED|SHIPPING") String type,
        @Positive long amount,
        @PositiveOrZero long minOrderVnd,
        @Positive Long maxDiscountVnd,
        @Positive Integer usageLimit,
        Instant startsAt,
        Instant expiresAt,
        Boolean isActive
) { }
