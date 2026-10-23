package com.catcheck.shop.domain;

import java.time.Instant;
import java.util.UUID;

public record PromoCode(
        UUID id,
        String code,
        String label,
        String type,
        long amount,
        long minOrderVnd,
        Long maxDiscountVnd,
        Integer usageLimit,
        int usedCount,
        Instant startsAt,
        Instant expiresAt,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {
    public boolean isValid(Instant now, long subtotal) {
        if (!isActive) return false;
        if (startsAt != null && now.isBefore(startsAt)) return false;
        if (expiresAt != null && now.isAfter(expiresAt)) return false;
        if (usageLimit != null && usedCount >= usageLimit) return false;
        if (subtotal < minOrderVnd) return false;
        return true;
    }

    public long calculateDiscount(long subtotal, long shippingFee) {
        long discount;
        if ("PERCENT".equalsIgnoreCase(type)) {
            discount = Math.round((subtotal * amount) / 100.0);
            if (maxDiscountVnd != null && maxDiscountVnd > 0) {
                discount = Math.min(discount, maxDiscountVnd);
            }
        } else if ("SHIPPING".equalsIgnoreCase(type)) {
            discount = Math.min(shippingFee, amount);
        } else {
            discount = Math.min(subtotal + shippingFee, amount);
        }
        return Math.max(0, discount);
    }
}
