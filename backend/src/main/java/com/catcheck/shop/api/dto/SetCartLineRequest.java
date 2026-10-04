package com.catcheck.shop.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record SetCartLineRequest(@Min(1) @Max(99) int quantity) { }
