package com.catcheck.cat.application.spi;

import java.util.UUID;

public interface UserAccountPort {

    AccountStatus statusOf(UUID userId);

    void advanceOnboardingStatus(UUID userId, OnboardingMilestone milestone);
}
