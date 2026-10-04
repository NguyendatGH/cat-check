package com.catcheck.shop.api.dto;

import com.catcheck.shop.domain.CartLine;

public record CartLineResponse(ProductResponse product, int quantity, long totalVnd) {
    public static CartLineResponse from(CartLine l) { return new CartLineResponse(ProductResponse.from(l.product()), l.quantity(), l.totalVnd()); }
}
