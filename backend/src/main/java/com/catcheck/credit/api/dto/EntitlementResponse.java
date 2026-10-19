package com.catcheck.credit.api.dto;

import com.catcheck.credit.domain.Entitlement;
import com.catcheck.credit.domain.PlanFeature;

import java.time.Instant;

public record EntitlementResponse(
        String currentPackage,
        Integer maxCatProfiles,
        boolean hasHistory,
        boolean hasTrend,
        boolean hasReminder,
        boolean hasExport,
        boolean storeImage,
        Instant writeAccessUntil,
        int trialScansRemaining
) {

    public static EntitlementResponse from(Entitlement entitlement, int trialScanLimit) {
        return new EntitlementResponse(
                entitlement.highestPackage(),
                entitlement.maxCatProfiles(),
                entitlement.isFeatureEnabled(PlanFeature.HISTORY),
                entitlement.isFeatureEnabled(PlanFeature.TREND),
                entitlement.isFeatureEnabled(PlanFeature.REMINDER),
                entitlement.isFeatureEnabled(PlanFeature.EXPORT),
                entitlement.isFeatureEnabled(PlanFeature.STORE_IMAGE),
                entitlement.writeAccessUntil(),
                Math.max(0, trialScanLimit - entitlement.trialScansUsed()));
    }
}
