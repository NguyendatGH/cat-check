package com.catcheck.credit.domain;

import java.time.Instant;
import java.util.UUID;

public record Entitlement(
        UUID userId,
        String highestPackage,
        Integer maxCatProfiles,
        PlanFeatures features,
        Instant writeAccessUntil,
        int trialScansUsed,
        Instant recomputedAt
) {

    public Entitlement {
        if (userId == null) {
            throw new IllegalArgumentException("entitlement.userId phải có giá trị");
        }
        if (features == null) {
            features = PlanFeatures.none();
        }
        if (trialScansUsed < 0) {
            throw new IllegalArgumentException("entitlement.trialScansUsed không được âm: " + userId);
        }
    }

    public static Entitlement defaults(UUID userId, Instant now) {
        return new Entitlement(userId, null, null, PlanFeatures.none(), null, 0, now);
    }

    public boolean hasWriteAccessAt(Instant now) {
        return writeAccessUntil != null && writeAccessUntil.isAfter(now);
    }

    public boolean isFeatureEnabled(PlanFeature feature) {
        return features.isEnabled(feature);
    }
}
