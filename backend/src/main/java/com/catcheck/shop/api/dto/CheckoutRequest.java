package com.catcheck.shop.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CheckoutRequest(@NotBlank String paymentMethod, @NotBlank @Size(max = 120) String receiverName,
                              @NotBlank @Size(max = 32) String receiverPhone, @NotBlank @Size(max = 500) String shippingAddress) { }
