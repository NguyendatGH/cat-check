package com.catcheck.credit.application;

import com.catcheck.credit.domain.Entitlement;
import com.catcheck.credit.domain.PackagePlan;
import com.catcheck.credit.domain.PlanFeatures;
import com.catcheck.credit.domain.PlanTier;
import com.catcheck.credit.domain.port.CreditBatchPort;
import com.catcheck.credit.domain.port.PackagePlanPort;
import com.catcheck.credit.domain.port.UserEntitlementPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class EntitlementRecalculationService {

    private final UserEntitlementPort userEntitlementPort;
    private final CreditBatchPort creditBatchPort;
    private final PackagePlanPort packagePlanPort;

    public EntitlementRecalculationService(UserEntitlementPort userEntitlementPort,
                                            CreditBatchPort creditBatchPort,
                                            PackagePlanPort packagePlanPort) {
        this.userEntitlementPort = userEntitlementPort;
        this.creditBatchPort = creditBatchPort;
        this.packagePlanPort = packagePlanPort;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Entitlement recalculate(UUID userId, Instant now) {
        userEntitlementPort.lockRow(userId, now);
        Entitlement current = userEntitlementPort.findOrDefault(userId, now);

        String highestCode = current.highestPackage();
        for (String packageCode : creditBatchPort.findAllBatches(userId).stream()
                .map(batch -> batch.packageCode())
                .filter(Objects::nonNull)
                .toList()) {
            highestCode = PlanTier.isHigher(packageCode, highestCode) ? packageCode : highestCode;
        }

        PlanFeatures features = PlanFeatures.none();
        Integer maxCatProfiles = null;
        if (highestCode != null) {
            Optional<PackagePlan> plan = packagePlanPort.findByCode(highestCode);
            if (plan.isPresent()) {
                features = plan.get().features();
                maxCatProfiles = plan.get().maxCatProfiles();
            }
        }

        Instant writeAccessUntil = userEntitlementPort.maxActivatedBatchExpiry(userId).orElse(null);

        Entitlement refreshed = new Entitlement(
                userId, highestCode, maxCatProfiles, features, writeAccessUntil,
                current.trialScansUsed(), now);
        userEntitlementPort.save(refreshed);
        return refreshed;
    }
}
