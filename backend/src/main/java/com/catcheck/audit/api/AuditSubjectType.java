package com.catcheck.audit.api;

/**
 * Loai du lieu bi tac dong. Khop {@code audit_log.subject_type}
 * (p4 §4.4.3, CHECK {@code ck_audit_subject_type}).
 */
public enum AuditSubjectType {

    USER,
    CAT,
    SCAN,
    SUBSCRIPTION,
    DOG,
    PACK,
    ARTICLE,
    CONSENT,
    POLICY,
    NOTIFICATION,
    SETTING,
    JOB,
    SYSTEM
}
