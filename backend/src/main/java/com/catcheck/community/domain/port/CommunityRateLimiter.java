package com.catcheck.community.domain.port;

import com.catcheck.community.domain.CommunityRateLimitRule;

public interface CommunityRateLimiter {

    boolean tryConsume(CommunityRateLimitRule rule, String subject);

    long secondsUntilRefill(CommunityRateLimitRule rule, String subject);

    default RetryAfter rejectIfLimited(CommunityRateLimitRule rule, String subject) {
        return tryConsume(rule, subject) ? null : new RetryAfter(secondsUntilRefill(rule, subject));
    }

    record RetryAfter(long seconds) {

        public long headerSeconds() {
            return Math.max(1, seconds);
        }
    }
}
