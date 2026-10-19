package com.catcheck.shop.domain;

import java.util.UUID;

public record ExpiredPayment(UUID orderId, long payosOrderCode, long totalVnd) { }
