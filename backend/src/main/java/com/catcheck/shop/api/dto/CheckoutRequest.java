package com.catcheck.shop.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CheckoutRequest(
        @NotBlank @Pattern(regexp = "(?i)COD|PAYOS") String paymentMethod,
        @NotBlank @Size(max = 120) String receiverName,
        @NotBlank @Size(max = 32) String receiverPhone,
        @NotBlank @Size(max = 500) String shippingAddress,
        @Size(max = 50) String promoCode) {

    public CheckoutRequest(String paymentMethod, String receiverName, String receiverPhone, String shippingAddress) {
        this(paymentMethod, receiverName, receiverPhone, shippingAddress, null);
    }
}
