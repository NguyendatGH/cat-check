package com.catcheck.identity.domain;

public enum SessionRevokeReason {

    USER_LOGOUT,
    USER_REVOKE_ONE,
    USER_REVOKE_ALL,
    PASSWORD_CHANGED,
    ADMIN_LOCK,
    DELETION_REQUESTED,
    MAX_SESSIONS,
    EXPIRED
}
