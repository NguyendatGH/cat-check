package com.catcheck.privacy.api.dto;

public record PurposeView(
        String code,
        String label,
        String description,
        boolean mandatory,
        boolean sensitive,
        boolean defaultState,
        String withdrawEffect,
        int phase
) {
}
