package com.catcheck.identity.domain;

/**
 * Ly do phien bi thu hoi. Khop {@code user_device_session.revoke_reason}
 * (p4 §4.4.2, CHECK {@code ck_device_session_reason}).
 */
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
