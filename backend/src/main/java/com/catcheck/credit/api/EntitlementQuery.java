package com.catcheck.credit.api;

import com.catcheck.credit.domain.Entitlement;

import java.util.UUID;

public interface EntitlementQuery {

    Entitlement snapshot(UUID userId);

    Integer maxCatProfiles(UUID userId);

    boolean isFeatureEnabled(UUID userId, Feature feature);

    enum Feature {
        HISTORY,
        TREND,
        REMINDER,
        EXPORT,
        STORE_IMAGE
    }

    boolean hasWriteAccess(UUID userId);

    CatProfileAllowance checkCatProfileAllowance(UUID userId, int currentCount);

    record CatProfileAllowance(
            boolean allowed,
            int currentCount,
            Integer maxCatProfiles,
            String currentPackage,
            String requiredPackage
    ) {
    }
}
