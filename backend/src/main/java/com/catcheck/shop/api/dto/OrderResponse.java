package com.catcheck.shop.api.dto;

import com.catcheck.shop.domain.Order;

import java.time.Instant;
import java.util.List;

public record OrderResponse(String id, String orderCode, String status, String paymentMethod, String receiverName,
                            String receiverPhone, String shippingAddress, long subtotalVnd, long discountVnd,
                            long shippingFeeVnd, long totalVnd, List<CartLineResponse> lines, Instant createdAt) {
    public static OrderResponse from(Order o) { return new OrderResponse(o.id().toString(), o.orderCode(), o.status(), o.paymentMethod(), o.receiverName(), o.receiverPhone(), o.shippingAddress(), o.subtotalVnd(), o.discountVnd(), o.shippingFeeVnd(), o.totalVnd(), o.lines().stream().map(CartLineResponse::from).toList(), o.createdAt()); }
}
