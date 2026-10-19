package com.catcheck.credit.infrastructure.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record ActivationPepperProperties(
        @Value("${ACTIVATION_PEPPER:}") String pepper,
        @Value("${ACTIVATION_PEPPER_VERSION:1}") int version
) {

    public ActivationPepperProperties {
        if (pepper == null) {
            pepper = "";
        }
        if (version < 1) {
            throw new IllegalStateException(
                    "ACTIVATION_PEPPER_VERSION phải >= 1, nhận: " + version);
        }
    }

    public short pepperVersion() {
        return (short) version;
    }
}
