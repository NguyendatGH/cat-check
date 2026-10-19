package com.catcheck.scan.application.spi;

import java.util.UUID;

public interface ImageRetentionConsentPort {

    boolean isGranted(UUID userId);
}
