package com.catcheck.privacy.spi;

import java.util.UUID;

public interface StepUpVerificationPort {

    boolean isVerified(UUID userId, String scope);
}
