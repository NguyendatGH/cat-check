package com.catcheck.identity.domain.port;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface AuthenticatedSessionGateway {

    void establish(UUID userId, String email, Set<String> roles, boolean rememberMe);

    void invalidateCurrent();

    void markReauthenticated(int windowSeconds);

    void markMfaTotpVerified();

    Optional<UUID> currentUserId();

    Optional<String> currentEmail();

    Optional<String> currentSessionIdHash();

    boolean isReauthenticated();
}
