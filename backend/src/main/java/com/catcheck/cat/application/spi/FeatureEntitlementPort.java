package com.catcheck.cat.application.spi;

import java.util.UUID;

public interface FeatureEntitlementPort {

    boolean isFeatureEnabled(UUID userId, String featureKey);
}
