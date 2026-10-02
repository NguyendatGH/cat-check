package com.catcheck.audit.api;

/**
 * Loai chu the. Khop {@code audit_log.actor_type}
 * (p4 §4.4.3, CHECK {@code ck_audit_actor_type}).
 */
public enum AuditActorType {

    /** Nguoi dung da dang nhap. */
    USER,

    /** Nhan vien quan tri. Vai tro cu the nam o {@link AuditActor#role()}. */
    ADMIN,

    /** Data Protection Officer. */
    DPO,

    /** He thong, khong co nguoi dung cu the. */
    SYSTEM,

    /** Job nen. {@code actor_id} null. */
    JOB
}
