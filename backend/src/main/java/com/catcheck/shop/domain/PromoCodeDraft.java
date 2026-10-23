package com.catcheck.shop.domain;

import java.time.Instant;

public record PromoCodeDraft(
        String code,
        String label,
        String type,
        long amount,
        long minOrderVnd,
        Long maxDiscountVnd,
        Integer usageLimit,
        Instant startsAt,
        Instant expiresAt,
        boolean isActive
) { }
