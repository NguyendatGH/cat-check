package com.catcheck.privacy.spi;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Registration consent payload owned by privacy and published synchronously by identity.
 * Keeping the contract in the receiving module's SPI preserves the identity -> privacy DAG.
 */
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
