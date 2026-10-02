package com.catcheck.identity.domain;

/**
 * No luu avatar. Khop {@code app_user.avatar_storage_provider}
 * (p4 §4.4.2, CHECK {@code ck_app_user_avatar_provider}).
 */
public enum StorageProvider {

    /** Luu trong DB {@code media_asset} cua module media (M1). */
    LOCAL,

    /** Luu tren Cloudinary. */
    CLOUDINARY
}
