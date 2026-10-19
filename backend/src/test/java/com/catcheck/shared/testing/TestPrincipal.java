package com.catcheck.shared.testing;

import com.catcheck.shared.security.MfaLevel;
import com.catcheck.shared.security.SecurityPrincipal;

import java.util.Set;
import java.util.UUID;

public record TestPrincipal(UUID userId, String email, Set<String> roles) implements SecurityPrincipal {

    public static TestPrincipal withRoles(String... roles) {
        return new TestPrincipal(UUID.randomUUID(), "admin@catcheck.vn", Set.of(roles));
    }

    @Override
    public MfaLevel mfaLevel() {
        return MfaLevel.TOTP;
    }
}
