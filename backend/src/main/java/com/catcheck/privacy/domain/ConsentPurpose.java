package com.catcheck.privacy.domain;

import java.time.Instant;

public record ConsentPurpose(
        String code,
        String labelVy,
        String labelEn,
        String descriptionVy,
        String descriptionEn,
        boolean isMandatory,
        boolean isSensitiveData,
        boolean defaultState,
        int phase,
        int displayOrder,
        boolean active,
        String withdrawEffect,
        Instant createdAt
) {

    public ConsentPurpose {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("consentPurpose.code không được trống");
        }
        if (labelVy == null || descriptionVy == null) {
            throw new IllegalArgumentException("consentPurpose bắt buộc có label_vi và description_vi");
        }
        if (!isMandatory && defaultState) {
            throw new IllegalArgumentException(
                    "Mục đích tuỳ chọn không được tick sẵn (ck_consent_purpose_default_off): " + code);
        }
    }
}
