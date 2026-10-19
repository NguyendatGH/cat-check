package com.catcheck.identity.domain.port;

import com.catcheck.identity.domain.MfaTotp;
import com.catcheck.identity.domain.TotpStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface MfaTotpRepository {

    Optional<MfaTotp> findByUserId(UUID userId);

    void insert(MfaTotp totp);

    void markActive(UUID userId, Instant activatedAt, long lastUsedStep);

    void updateLastUsedStep(UUID userId, long step);

    int incrementFailedCount(UUID userId);

    void resetFailedCount(UUID userId);

    void lock(UUID userId, Instant lockedUntil);

    void delete(UUID userId);

    long countActive();
}
