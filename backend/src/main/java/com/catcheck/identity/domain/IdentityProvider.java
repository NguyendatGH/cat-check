package com.catcheck.identity.domain;

/**
 * Phuong thuc dang nhap. Khop {@code user_identity.provider}
 * (p4 §4.4.2, CHECK {@code ck_identity_provider}).
 */
public enum IdentityProvider {

    /** Email + mat khau BCrypt. */
    LOCAL,

    /** Google OAuth. {@code provider_user_id} luu claim {@code sub}, KHONG luu email. */
    GOOGLE;

    /**
     * P11 §11.1.1: moi tai khoan luon it nhat mot identity LOCAL, ke ca khi dang nhap
     * bang Google lan dau. Dung cho phep doi mat khau va gan phien vao sau do.
     */
    public boolean requiresPasswordHash() {
        return this == LOCAL;
    }
}
