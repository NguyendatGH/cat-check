package com.catcheck.credit.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "catcheck.credit.welcome-bonus")
public record WelcomeBonusProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("10") int amount,
        @DefaultValue("5d") Duration validity
) {

    public WelcomeBonusProperties {
        if (amount <= 0) {
            throw new IllegalArgumentException("catcheck.credit.welcome-bonus.amount phải > 0: " + amount);
        }
        if (validity == null || validity.isNegative() || validity.isZero()) {
            throw new IllegalArgumentException("catcheck.credit.welcome-bonus.validity phải > 0");
        }
    }
}
