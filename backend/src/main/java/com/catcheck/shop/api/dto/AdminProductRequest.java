package com.catcheck.shop.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminProductRequest(
        @Size(max = 64) String sku,
        @NotBlank @Size(max = 180) String name,
        @NotBlank @Size(max = 4000) String description,
        @Size(max = 2000) String imageUrl,
        @NotNull @Min(0) Long priceVnd,
        @Min(0) Long compareAtPriceVnd,
        @NotNull @Min(0) Integer stockQuantity,
        @NotNull @Pattern(regexp = "DRAFT|PUBLISHED|ARCHIVED") String status) { }
