package com.catcheck.shop.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code paymentMethod} khớp {@code ck_shop_order_payment}; không chặn ở đây thì giá trị lạ thành 500 ở DB. */
public record CheckoutRequest(@NotBlank @Pattern(regexp = "(?i)COD|MOMO|BANK_TRANSFER") String paymentMethod,
                              @NotBlank @Size(max = 120) String receiverName,
                              @NotBlank @Size(max = 32) String receiverPhone, @NotBlank @Size(max = 500) String shippingAddress) { }
