package com.catcheck.cat.application.spi;

public enum OnboardingMilestone {

    ACCOUNT_ONLY,
    CAT_CREATED,
    SURVEY_DONE_OR_SKIPPED,
    COMPLETED;

    public String wire() {
        return name();
    }
}
