package com.catcheck.credit.api.dto;

import com.catcheck.credit.domain.PackagePlan;

import java.math.BigDecimal;

/**
 * F3 — một gói trong {@code GET /reference/packages} (p8 §8.4.6).
 *
 * <p>Cờ tính năng trả về dạng PHẲNG ({@code hasTrend}, {@code hasExport}…) thay vì lồng nguyên
 * khối {@code features} JSONB, để khớp đúng quy ước {@code EntitlementResponse} (H4) mà client
 * đã dùng — hai endpoint nói về cùng một tập quyền thì không nên có hai hình dạng khác nhau.</p>
 *
 * <p>KHÔNG trả {@code active} (danh mục này chỉ gồm gói đang bán) và KHÔNG trả {@code version}
 * (số nội bộ để entitlement so sánh, người dùng cuối không cần).</p>
 *
 * @param maxCatProfiles {@code null} = không giới hạn (p4 I27)
 */
public record PackagePlanResponse(
        String code,
        String name,
        BigDecimal weightKg,
        int creditAmount,
        int creditValidityDays,
        Integer maxCatProfiles,
        String historyLevel,
        boolean hasTrend,
        boolean hasReminder,
        boolean hasExport,
        boolean storeImage
) {

    public static PackagePlanResponse from(PackagePlan plan) {
        return new PackagePlanResponse(
                plan.code(),
                plan.name(),
                plan.weightKg(),
                plan.creditAmount(),
                plan.creditValidityDays(),
                plan.maxCatProfiles(),
                plan.features().history().name(),
                plan.features().trend(),
                plan.features().reminder(),
                plan.features().export(),
                plan.features().storeImage());
    }
}
