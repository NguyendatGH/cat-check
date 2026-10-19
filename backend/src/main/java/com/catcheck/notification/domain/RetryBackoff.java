package com.catcheck.notification.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class RetryBackoff {

    public static final int DEFAULT_MAX_ATTEMPTS = 5;

    private static final List<Duration> DELAYS = List.of(
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(30),
            Duration.ofHours(2),
            Duration.ofHours(12));

    private RetryBackoff() {
    }

    public static Duration delayAfterFailure(int attempt) {
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt phải >= 1, nhận " + attempt);
        }
        return DELAYS.get(Math.min(attempt, DELAYS.size()) - 1);
    }

    public static Instant nextAttemptAt(Instant now, int attempt) {
        return now.plus(delayAfterFailure(attempt));
    }

    public static boolean isExhausted(int attempts, int maxAttempts) {
        return attempts >= maxAttempts;
    }

    public static List<Duration> delays() {
        return DELAYS;
    }
}
