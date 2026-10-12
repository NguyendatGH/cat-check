package com.catcheck.notification.domain;

public enum PushRevokeReason {
    FCM_UNREGISTERED,
    FCM_INVALID,
    USER_DISABLED,
    CONSENT_WITHDRAWN,
    ACCOUNT_DELETION,
    STALE
}
