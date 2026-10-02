package com.catcheck.privacy.application;

/**
 * Một mục khai báo đồng ý từ client — dùng chung cho đăng ký (p8 §8.5.1
 * {@code consents: [{purposeCode, granted}]}) và C3.
 *
 * @param purposeCode mã mục đích — phải tồn tại trong {@code consent_purpose}
 * @param granted     {@code true} = cấp, {@code false} = rút/từ chối
 */
public record ConsentGrant(String purposeCode, boolean granted) {

    public ConsentGrant {
        if (purposeCode == null || purposeCode.isBlank()) {
            throw new IllegalArgumentException("consentGrant.purposeCode không được trống");
        }
    }
}
