package com.catcheck.privacy.domain.port;

import java.util.UUID;

public interface ConsentStatePort {

    boolean isGranted(UUID userId, String purposeCode);
}
