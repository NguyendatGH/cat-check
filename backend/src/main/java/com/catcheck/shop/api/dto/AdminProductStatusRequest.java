package com.catcheck.shop.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record AdminProductStatusRequest(@NotNull @Pattern(regexp = "DRAFT|PUBLISHED|ARCHIVED") String status) { }
