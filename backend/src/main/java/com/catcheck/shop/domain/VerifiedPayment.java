package com.catcheck.shop.domain;

/** success is true exactly when the PayOS webhook code is "00". */
public record VerifiedPayment(long payosOrderCode, long amountVnd, String reference, boolean success) { }
