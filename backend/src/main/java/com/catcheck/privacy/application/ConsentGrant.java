package com.catcheck.privacy.application;

public record ConsentGrant(String purposeCode, boolean granted) {

    public ConsentGrant {
        if (purposeCode == null || purposeCode.isBlank()) {
            throw new IllegalArgumentException("consentGrant.purposeCode không được trống");
        }
    }
}
