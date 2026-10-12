package com.catcheck.identity.domain.port;

import com.catcheck.identity.domain.RateLimitRule;

public interface RateLimiter {

    boolean tryConsume(RateLimitRule rule, String subject);

    long secondsUntilRefill(RateLimitRule rule, String subject);

    default RetryAfter rejectIfLimited(RateLimitRule rule, String subject) {
        return tryConsume(rule, subject) ? null : new RetryAfter(secondsUntilRefill(rule, subject));
    }

    record RetryAfter(long seconds) {

        public long headerSeconds() {
            return Math.max(1, seconds);
        }
    }
}
