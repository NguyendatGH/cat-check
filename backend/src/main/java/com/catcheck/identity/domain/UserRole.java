package com.catcheck.identity.domain;

public enum UserRole {

    USER,
    ADMIN_SUPPORT,
    ADMIN_CATALOG,
    ADMIN_SUPER,
    DPO,
    MODERATOR,
    VET;

    public boolean isAdmin() {
        return this == ADMIN_SUPPORT || this == ADMIN_CATALOG || this == ADMIN_SUPER;
    }
}
