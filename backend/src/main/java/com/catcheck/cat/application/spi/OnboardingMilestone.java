package com.catcheck.cat.application.spi;

/**
 * Cot moc onboarding ma module {@code cat} co the day tien ({@code app_user.onboarding_status}).
 *
 * <p>Khop CHECK constraint cua {@code app_user.onboarding_status} (p4 §4.4, dong 1182): p4 ghi ro
 * day la <b>cot that, KHONG suy luan</b> tu viec co/khong co ho so meo — suy luan gian tiep sai
 * ngay khi user xoa con meo duy nhat. Thu tu khai bao la thu tu tien trinh, dung cho phep so sanh
 * "chi tien, khong lui" o {@code UserAccountPort#advanceOnboardingStatus}.</p>
 *
 * <p>Enum rieng cua {@code cat} (khong import {@code identity.domain.OnboardingStatus}) de giu
 * dung khuon SPI san co trong package nay — xem {@code AccountStatus#fromWire}.</p>
 */
public enum OnboardingMilestone {

    ACCOUNT_ONLY,
    CAT_CREATED,
    SURVEY_DONE_OR_SKIPPED,
    COMPLETED;

    /** Gia tri ghi xuong cot {@code onboarding_status}. */
    public String wire() {
        return name();
    }
}
