package com.catcheck.credit.domain.port;

import com.catcheck.credit.domain.ActivationRateLimitRule;

public interface ActivationRateLimiter {

    boolean tryConsume(ActivationRateLimitRule rule, String subject);

    long secondsUntilRefill(ActivationRateLimitRule rule, String subject);

    default RetryAfter rejectIfLimited(ActivationRateLimitRule rule, String subject) {
        return tryConsume(rule, subject) ? null : new RetryAfter(secondsUntilRefill(rule, subject));
    }

    record RetryAfter(long seconds) {

        public long headerSeconds() {
            return Math.max(1, seconds);
        }
    }
}
