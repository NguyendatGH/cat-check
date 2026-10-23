package com.catcheck.shop.api.dto;

import com.catcheck.shop.domain.PromoCode;

import java.time.Instant;

public record AdminPromoCodeResponse(
        String id,
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
    public static AdminPromoCodeResponse from(PromoCode p) {
        return new AdminPromoCodeResponse(
                p.id().toString(),
                p.code(),
                p.label(),
                p.type(),
                p.amount(),
                p.minOrderVnd(),
                p.maxDiscountVnd(),
                p.usageLimit(),
                p.usedCount(),
                p.startsAt(),
                p.expiresAt(),
                p.isActive(),
                p.createdAt(),
                p.updatedAt()
        );
    }
}
