package com.catcheck.privacy.api;

import com.catcheck.shared.security.MfaLevel;
import com.catcheck.shared.security.SecurityPrincipal;

import java.util.Set;
import java.util.UUID;

record AdminTestPrincipal(UUID userId, String email, Set<String> roles) implements SecurityPrincipal {

    static AdminTestPrincipal of(UUID userId, String role) {
        return new AdminTestPrincipal(userId, "staff@catcheck.vn", Set.of(role));
    }

    @Override
    public MfaLevel mfaLevel() {
        return MfaLevel.TOTP;
    }
}
