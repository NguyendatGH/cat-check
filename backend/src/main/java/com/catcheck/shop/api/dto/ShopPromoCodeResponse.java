package com.catcheck.shop.api.dto;

import com.catcheck.shop.domain.PromoCode;

public record ShopPromoCodeResponse(
        String code,
        String label,
        String type,
        long amount
) {
    public static ShopPromoCodeResponse from(PromoCode p) {
        return new ShopPromoCodeResponse(p.code(), p.label(), p.type(), p.amount());
    }
}
