package com.catcheck.shop.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminOrderStatusRequest(@NotBlank String status, @Size(max = 500) String reason) { }
