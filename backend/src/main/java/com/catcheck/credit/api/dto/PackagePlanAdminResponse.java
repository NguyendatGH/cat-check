package com.catcheck.credit.api.dto;

import com.catcheck.credit.domain.PackagePlanAdminView;

import java.math.BigDecimal;
import java.time.Instant;

public record PackagePlanAdminResponse(
        String code,
        String name,
        BigDecimal weightKg,
        int creditAmount,
        int creditValidityDays,
        Integer maxCatProfiles,
        PlanFeaturesView features,
        boolean active,
        int version,
        Instant updatedAt,
        String etag
) {

    public static PackagePlanAdminResponse from(PackagePlanAdminView view, String etag) {
        var plan = view.plan();
        return new PackagePlanAdminResponse(
                plan.code(),
                plan.name(),
                plan.weightKg(),
                plan.creditAmount(),
                plan.creditValidityDays(),
                plan.maxCatProfiles(),
                PlanFeaturesView.from(plan),
                plan.active(),
                plan.version(),
                view.updatedAt(),
                etag);
    }
}
