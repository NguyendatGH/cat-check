package com.catcheck.identity.domain;

public enum IdentityProvider {

    LOCAL,

    GOOGLE;

    public boolean requiresPasswordHash() {
        return this == LOCAL;
    }
}
