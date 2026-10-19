package com.catcheck.shop.api.dto;

import com.catcheck.shop.domain.Order;
import com.catcheck.shop.domain.OrderStatusPolicy;

import java.time.Instant;
import java.util.List;

/** Order as staff see it: receiver details are needed to ship; PayOS internals (link, order code) are not. */
public record AdminOrderResponse(String id, String orderCode, String status, String paymentMethod, String receiverName,
                                 String receiverPhone, String shippingAddress, long subtotalVnd, long discountVnd,
                                 long shippingFeeVnd, long totalVnd, List<CartLineResponse> lines, Instant createdAt,
                                 Instant paymentExpiresAt, Instant paidAt, String paymentReference, String cancelReason,
                                 boolean refundNeeded, List<String> allowedNextStatuses) {
    public static AdminOrderResponse from(Order o) {
        return new AdminOrderResponse(o.id().toString(), o.orderCode(), o.status(), o.paymentMethod(), o.receiverName(), o.receiverPhone(), o.shippingAddress(), o.subtotalVnd(), o.discountVnd(), o.shippingFeeVnd(), o.totalVnd(),
                o.lines().stream().map(CartLineResponse::from).toList(), o.createdAt(), o.paymentExpiresAt(), o.paidAt(), o.paymentReference(), o.cancelReason(), o.refundNeeded(),
                OrderStatusPolicy.adminNextStatuses(o.paymentMethod(), o.status()));
    }
}
