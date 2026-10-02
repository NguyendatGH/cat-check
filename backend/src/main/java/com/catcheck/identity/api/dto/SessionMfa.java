package com.catcheck.identity.api.dto;

/** Thông tin MFA lồng trong {@link SessionResponse} (p8 §8.4.4 nhóm A). */
public record SessionMfa(
        boolean totpEnabled,
        String mfaLevel,
        Long recoveryCodesRemaining) {
}
