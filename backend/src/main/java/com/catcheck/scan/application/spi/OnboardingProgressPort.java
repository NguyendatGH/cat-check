package com.catcheck.scan.application.spi;

import java.util.UUID;

public interface OnboardingProgressPort {

    void markFirstScanCompleted(UUID userId);
}
