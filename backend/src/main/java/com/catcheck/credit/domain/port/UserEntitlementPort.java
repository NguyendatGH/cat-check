package com.catcheck.credit.domain.port;

import com.catcheck.credit.domain.Entitlement;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserEntitlementPort {

    Optional<Entitlement> find(UUID userId);

    Entitlement findOrDefault(UUID userId, Instant now);

    void ensureRow(UUID userId, Instant now);

    void lockRow(UUID userId, Instant now);

    void save(Entitlement entitlement);

    boolean incrementTrialScansUsed(UUID userId, int trialScanLimit, Instant now);

    Optional<Instant> maxActivatedBatchExpiry(UUID userId);
}
