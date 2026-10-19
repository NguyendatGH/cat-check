package com.catcheck.identity.domain.port;

import java.time.Instant;
import java.util.UUID;

public interface PasswordResetRepository {

    void insert(UUID userId, String tokenHash, Instant expiresAt, String requestIp);

    void markConsumed(String tokenHash, Instant consumedAt);

    boolean isConsumed(String tokenHash);
}
