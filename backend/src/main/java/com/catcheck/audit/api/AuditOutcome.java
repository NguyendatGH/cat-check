package com.catcheck.audit.api;

/**
 * Ket qua cua hanh dong. Khop {@code audit_log.result}
 * (p4 §4.4.3, CHECK {@code ck_audit_result}).
 */
public enum AuditOutcome {

    SUCCESS,

    /**
     * Da chan (sai mat khau, thieu quyen, vuot rate limit...).
     *
     * <p>CHECK {@code ck_audit_denied_has_action} bat buoc {@code metadata.action} phai
     * co khi {@code result = 'DENIED'}: thay vi ghi {@code action = AUTH.LOGIN} cho ca
     * thanh cong va that bai (lam nhieu), ta ghi hanh dong that su trong
     * {@code metadata.action} va de {@code action} la loai mang.</p>
     */
    DENIED,

    /** Loi ky thuat hoac ngoai vi. */
    ERROR
}
