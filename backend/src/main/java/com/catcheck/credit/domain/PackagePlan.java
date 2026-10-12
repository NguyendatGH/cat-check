package com.catcheck.credit.domain;

import java.math.BigDecimal;

public record PackagePlan(
        String code,
        String name,
        BigDecimal weightKg,
        int creditAmount,
        int creditValidityDays,
        Integer maxCatProfiles,
        PlanFeatures features,
        boolean active,
        int version
) {

    public PackagePlan {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("packagePlan.code phải có giá trị");
        }
        if (creditAmount <= 0) {
            throw new IllegalArgumentException("packagePlan.creditAmount phải > 0: " + code);
        }
        if (creditValidityDays <= 0) {
            throw new IllegalArgumentException("packagePlan.creditValidityDays phải > 0: " + code);
        }
        if (features == null) {
            features = PlanFeatures.none();
        }
    }

    public int tierRank() {
        return PlanTier.rankOf(code);
    }
}
