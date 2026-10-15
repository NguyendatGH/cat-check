package com.catcheck.identity.domain.port;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface AuthenticatedSessionGateway {

    void establish(UUID userId, String email, Set<String> roles, boolean rememberMe);

    /** TOTP con thieu: chi ghi trang thai cho buoc 2 (userId + han), chua mo phien. */
    void beginPendingMfa(UUID userId, String email, Set<String> roles, boolean rememberMe);

    Optional<UUID> pendingMfaUserId();

    /** Buoc 2 da qua: mo phien tu trang thai cho va nang mfaLevel len TOTP. */
    void completePendingMfa();

    void invalidateCurrent();

    void markReauthenticated(int windowSeconds);

    void markMfaTotpVerified();

    Optional<UUID> currentUserId();

    Optional<String> currentEmail();

    Optional<String> currentSessionIdHash();

    boolean isReauthenticated();
}
