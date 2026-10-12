package com.catcheck.ai.domain.port;

import com.catcheck.ai.domain.AiChatRateLimitRule;

public interface AiChatRateLimiter {

    boolean tryConsume(AiChatRateLimitRule rule, String subject);

    long secondsUntilRefill(AiChatRateLimitRule rule, String subject);

    default RetryAfter rejectIfLimited(AiChatRateLimitRule rule, String subject) {
        return tryConsume(rule, subject) ? null : new RetryAfter(secondsUntilRefill(rule, subject));
    }

    record RetryAfter(long seconds) {

        public long headerSeconds() {
            return Math.max(1, seconds);
        }
    }
}
