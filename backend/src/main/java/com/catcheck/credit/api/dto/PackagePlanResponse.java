package com.catcheck.credit.api.dto;

import com.catcheck.credit.domain.PackagePlan;

import java.math.BigDecimal;

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
