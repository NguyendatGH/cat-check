package com.catcheck.identity.domain;

/**
 * Vai tro cua tai khoan. Khop {@code user_role.role} va {@code audit_log.actor_role}
 * (p4 §4.4.2, CHECK {@code ck_user_role_role}).
 *
 * <p>P11 §11.5.1: khong co phan cap vai tro — moi quyen duoc kiem tra ngang hang
 * bang role cu the, khong suy dien {@code ADMIN_SUPER} co quyen {@code ADMIN_CATALOG}.</p>
 */
public enum UserRole {

    USER,
    ADMIN_SUPPORT,
    ADMIN_CATALOG,
    ADMIN_SUPER,
    DPO,
    MODERATOR,
    VET;

    /** Co quen truy cap phan he thong, dung cho {@code audit_log.actor_type = 'ADMIN'}. */
    public boolean isAdmin() {
        return this == ADMIN_SUPPORT || this == ADMIN_CATALOG || this == ADMIN_SUPER;
    }
}
