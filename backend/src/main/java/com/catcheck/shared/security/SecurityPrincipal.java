package com.catcheck.shared.security;

import java.io.Serializable;
import java.util.Set;
import java.util.UUID;

public interface SecurityPrincipal extends Serializable {

    UUID userId();

    String email();

    Set<String> roles();

    default MfaLevel mfaLevel() {
        return MfaLevel.NONE;
    }
}
