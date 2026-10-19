package com.catcheck.credit.application;

import com.catcheck.credit.api.EntitlementQuery;
import com.catcheck.credit.domain.Entitlement;
import com.catcheck.credit.domain.PackagePlan;
import com.catcheck.credit.domain.PlanFeature;
import com.catcheck.credit.domain.PlanTier;
import com.catcheck.credit.domain.port.PackagePlanPort;
import com.catcheck.credit.domain.port.UserEntitlementPort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

@Service
public class EntitlementService implements EntitlementQuery {

    private final UserEntitlementPort userEntitlementPort;
    private final PackagePlanPort packagePlanPort;
    private final Clock clock;

    public EntitlementService(
            UserEntitlementPort userEntitlementPort, PackagePlanPort packagePlanPort, Clock clock) {
        this.userEntitlementPort = userEntitlementPort;
        this.packagePlanPort = packagePlanPort;
        this.clock = clock;
    }

    @Override
    public Entitlement snapshot(UUID userId) {
        return userEntitlementPort.findOrDefault(userId, clock.instant());
    }

    @Override
    public Integer maxCatProfiles(UUID userId) {
        return snapshot(userId).maxCatProfiles();
    }

    @Override
    public boolean isFeatureEnabled(UUID userId, EntitlementQuery.Feature feature) {
        return snapshot(userId).isFeatureEnabled(PlanFeature.valueOf(feature.name()));
    }

    @Override
    public boolean hasWriteAccess(UUID userId) {
        return snapshot(userId).hasWriteAccessAt(clock.instant());
    }

    @Override
    public CatProfileAllowance checkCatProfileAllowance(UUID userId, int currentCount) {
        Entitlement entitlement = snapshot(userId);
        Integer max = entitlement.maxCatProfiles();
        if (max == null) {
            return new CatProfileAllowance(true, currentCount, null, entitlement.highestPackage(), null);
        }
        boolean allowed = currentCount < max;
        return new CatProfileAllowance(
                allowed, currentCount, max, entitlement.highestPackage(),
                allowed ? null : lowestPackageWithHigherLimit(max));
    }

    private String lowestPackageWithHigherLimit(int currentMax) {
        for (PlanTier tier : PlanTier.values()) {
            Optional<PackagePlan> plan = packagePlanPort.findByCode(tier.code());
            if (plan.isPresent()
                    && plan.get().active()
                    && plan.get().maxCatProfiles() != null
                    && plan.get().maxCatProfiles() > currentMax) {
                return tier.code();
            }
        }
        return null;
    }

}
