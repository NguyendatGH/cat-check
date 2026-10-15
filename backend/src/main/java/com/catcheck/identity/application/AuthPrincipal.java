package com.catcheck.identity.application;

import com.catcheck.shared.security.MfaLevel;
import com.catcheck.shared.security.SecurityPrincipal;

import java.io.Serializable;
import java.security.Principal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record AuthPrincipal(
        UUID userId,
        String email,
        Set<String> roles,
        Instant authenticatedAt,
        Instant reauthenticatedUntil,
        MfaLevel mfaLevel) implements Serializable, Principal, SecurityPrincipal {

    public AuthPrincipal {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        mfaLevel = mfaLevel == null ? MfaLevel.NONE : mfaLevel;
    }

    @Override
    public String getName() {
        return userId.toString();
    }

    public static AuthPrincipal of(UUID userId, String email, Set<String> roles, Instant now) {
        return new AuthPrincipal(userId, email, roles, now, null, MfaLevel.NONE);
    }

    public AuthPrincipal withReauthenticatedUntil(Instant value) {
        return new AuthPrincipal(userId, email, roles, authenticatedAt, value, mfaLevel);
    }

    public boolean isReauthenticatedAt(Instant now) {
        return reauthenticatedUntil != null && now.isBefore(reauthenticatedUntil);
    }

    public AuthPrincipal withMfaLevel(MfaLevel value) {
        return new AuthPrincipal(userId, email, roles, authenticatedAt, reauthenticatedUntil, value);
    }
}
