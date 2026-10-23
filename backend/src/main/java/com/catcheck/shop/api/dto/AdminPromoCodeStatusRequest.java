package com.catcheck.shop.api.dto;

import jakarta.validation.constraints.NotNull;

public record AdminPromoCodeStatusRequest(@NotNull Boolean isActive) { }
