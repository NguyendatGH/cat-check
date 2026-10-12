package com.catcheck.privacy.spi;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RegistrationConsentEvent(
        UUID userId,
        List<ConsentGrant> consents,
        String locale,
        String ipAddress,
        String userAgent,
        String requestId,
        Instant occurredAt) {

    public RegistrationConsentEvent {
        consents = consents == null ? List.of() : List.copyOf(consents);
    }

    public record ConsentGrant(String purposeCode, boolean granted) {
    }
}
