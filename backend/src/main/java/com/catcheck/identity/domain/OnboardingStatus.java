package com.catcheck.identity.domain;

/**
 * Trang thai onboarding. Khop {@code app_user.onboarding_status}
 * (p4 §4.4.2, CHECK {@code ck_app_user_onboarding}).
 */
public enum OnboardingStatus {

    /** Chi co tai khoan, chua tao meo. */
    ACCOUNT_ONLY,

    /** Da tao meo, chua lam khao sat. */
    CAT_CREATED,

    /** Da lam hoac bo qua khao sat mau. */
    SURVEY_DONE_OR_SKIPPED,

    /** Hoan tat onboarding. */
    COMPLETED
}
