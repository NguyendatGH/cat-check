package com.catcheck.identity.domain;

public enum UserStatus {

    PENDING_VERIFICATION,

    ACTIVE,

    LOCKED,

    RESTRICTED,

    DELETION_REQUESTED,

    ANONYMIZED
}
