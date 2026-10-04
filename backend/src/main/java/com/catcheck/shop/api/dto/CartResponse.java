package com.catcheck.shop.api.dto;

import com.catcheck.shop.domain.CartLine;

import java.util.List;

public record CartResponse(List<CartLineResponse> lines, long subtotalVnd, long shippingFeeVnd, long totalVnd) {
    public static CartResponse from(List<CartLine> lines) {
        long subtotal = lines.stream().mapToLong(CartLine::totalVnd).sum();
        long shipping = subtotal >= 500000 || subtotal == 0 ? 0 : 30000;
        return new CartResponse(lines.stream().map(CartLineResponse::from).toList(), subtotal, shipping, subtotal + shipping);
    }
}
