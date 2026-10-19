package com.catcheck.identity.api.dto;

public record SessionMfa(
        boolean totpEnabled,
        String mfaLevel,
        Long recoveryCodesRemaining) {
}
