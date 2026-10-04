package com.catcheck.notification.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Backoff của outbox phải khớp ĐÚNG bảng p12 §12.4/§12.8.1: 1 phút / 5 phút / 30 phút / 2 giờ /
 * 12 giờ, tối đa 5 lần thử rồi dead letter.
 */
class RetryBackoffTest {

    private static final Instant NOW = Instant.parse("2026-10-03T10:00:00Z");

    @Test
    void delaysFollowTheSequenceInPart12() {
        assertThat(RetryBackoff.delays()).containsExactly(
                Duration.ofMinutes(1),
                Duration.ofMinutes(5),
                Duration.ofMinutes(30),
                Duration.ofHours(2),
                Duration.ofHours(12));
    }

    @Test
    void delayGrowsWithEachFailedAttempt() {
        assertThat(RetryBackoff.delayAfterFailure(1)).isEqualTo(Duration.ofMinutes(1));
        assertThat(RetryBackoff.delayAfterFailure(2)).isEqualTo(Duration.ofMinutes(5));
        assertThat(RetryBackoff.delayAfterFailure(3)).isEqualTo(Duration.ofMinutes(30));
        assertThat(RetryBackoff.delayAfterFailure(4)).isEqualTo(Duration.ofHours(2));
        assertThat(RetryBackoff.delayAfterFailure(5)).isEqualTo(Duration.ofHours(12));
    }

    /** Vượt bảng thì giữ mốc cuối — một dòng dữ liệu lạ không được làm chết job. */
    @Test
    void delayIsClampedToTheLastStep() {
        assertThat(RetryBackoff.delayAfterFailure(9)).isEqualTo(Duration.ofHours(12));
    }

    @Test
    void attemptMustBePositive() {
        assertThatThrownBy(() -> RetryBackoff.delayAfterFailure(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nextAttemptAtAddsTheDelayToNow() {
        assertThat(RetryBackoff.nextAttemptAt(NOW, 3)).isEqualTo(Instant.parse("2026-10-03T10:30:00Z"));
    }

    @Test
    void fiveAttemptsExhaustTheDefaultBudget() {
        int max = RetryBackoff.DEFAULT_MAX_ATTEMPTS;
        assertThat(max).isEqualTo(5);
        assertThat(RetryBackoff.isExhausted(4, max)).isFalse();
        assertThat(RetryBackoff.isExhausted(5, max)).isTrue();
        assertThat(RetryBackoff.isExhausted(6, max)).isTrue();
    }
}
