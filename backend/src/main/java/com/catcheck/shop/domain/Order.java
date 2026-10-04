package com.catcheck.shop.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Order(UUID id, String orderCode, String status, String paymentMethod, String receiverName,
                    String receiverPhone, String shippingAddress, long subtotalVnd, long discountVnd,
                    long shippingFeeVnd, long totalVnd, List<CartLine> lines, Instant createdAt) { }
