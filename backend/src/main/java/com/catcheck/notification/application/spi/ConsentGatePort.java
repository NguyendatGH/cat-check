package com.catcheck.notification.application.spi;

import java.util.UUID;

public interface ConsentGatePort {

    boolean isGranted(UUID userId, String purposeCode);
}
