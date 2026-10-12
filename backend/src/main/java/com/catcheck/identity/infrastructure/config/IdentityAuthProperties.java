package com.catcheck.identity.infrastructure.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "catcheck.auth")
@Validated
public record IdentityAuthProperties(

        @NotNull @DefaultValue("5m") Duration otpTtl,
        @Min(1) @Max(20) @DefaultValue("5") int otpMaxAttempts,
        @NotNull @DefaultValue("10m") Duration otpTicketTtl,
        @Min(6) @Max(8) @DefaultValue("6") int otpCodeLength,

        @Min(4) @Max(16) @DefaultValue("12") int bcryptStrength,
        @Min(8) @DefaultValue("8") int passwordMinLength,
        @Min(8) @DefaultValue("72") int passwordMaxLength,
        @Min(1) @DefaultValue("365") int commonPasswordListMaxAgeDays,

        @Min(1) @DefaultValue("5") int lockoutThresholdShort,
        @NotNull @DefaultValue("15m") Duration lockoutDurationShort,
        @Min(1) @DefaultValue("10") int lockoutThresholdLong,
        @NotNull @DefaultValue("1h") Duration lockoutDurationLong,

        @NotNull @DefaultValue("30d") Duration sessionMaxInactive,
        @NotNull @DefaultValue("12h") Duration sessionDefaultMaxInactive,
        @Min(1) @DefaultValue("5") int maxConcurrentSessions,

        @NotNull @DefaultValue("CATCHECK_SID") String sessionCookieName,

        @DefaultValue("true") boolean filterChainEnabled
) {
}
