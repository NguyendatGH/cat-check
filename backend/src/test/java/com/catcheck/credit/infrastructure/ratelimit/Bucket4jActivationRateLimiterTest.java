package com.catcheck.credit.infrastructure.ratelimit;

import com.catcheck.credit.domain.ActivationRateLimitRule;
import com.catcheck.credit.domain.port.ActivationRateLimiter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G3 ({@code docs/cat-rag-credit-pipeline.md} B2) — hạn mức gọi {@code POST /activations}.
 *
 * <p>Ngưỡng theo {@link ActivationRateLimitRule}: 5/15 phút theo user, 20/giờ theo IP (xem javadoc
 * của enum đó cho lý do chọn số — KHÔNG trích từ p11, context/ đã xoá).</p>
 */
class Bucket4jActivationRateLimiterTest {

    private final Bucket4jActivationRateLimiter limiter = new Bucket4jActivationRateLimiter();

    @Test
    void allowsExactlyFiveCallsPerUserThenBlocksTheSixth() {
        String userId = "00000000-0000-7000-8000-00000000a001";

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryConsume(ActivationRateLimitRule.ACTIVATION_BY_USER, userId))
                    .as("lần gọi thứ %d phải còn trong hạn mức", i + 1)
                    .isTrue();
        }
        assertThat(limiter.tryConsume(ActivationRateLimitRule.ACTIVATION_BY_USER, userId))
                .as("lần thứ 6 phải bị chặn")
                .isFalse();
        assertThat(limiter.secondsUntilRefill(ActivationRateLimitRule.ACTIVATION_BY_USER, userId))
                .as("phải báo số giây chờ > 0 khi đang bị chặn")
                .isPositive();
    }

    @Test
    void allowsExactlyTwentyCallsPerIpThenBlocksTheTwentyFirst() {
        String ip = "203.0.113.9";

        for (int i = 0; i < 20; i++) {
            assertThat(limiter.tryConsume(ActivationRateLimitRule.ACTIVATION_BY_IP, ip)).isTrue();
        }
        assertThat(limiter.tryConsume(ActivationRateLimitRule.ACTIVATION_BY_IP, ip)).isFalse();
    }

    @Test
    void differentUsersGetIndependentBuckets() {
        String userA = "00000000-0000-7000-8000-00000000a001";
        String userB = "00000000-0000-7000-8000-00000000a002";

        for (int i = 0; i < 5; i++) {
            limiter.tryConsume(ActivationRateLimitRule.ACTIVATION_BY_USER, userA);
        }
        assertThat(limiter.tryConsume(ActivationRateLimitRule.ACTIVATION_BY_USER, userA)).isFalse();
        assertThat(limiter.tryConsume(ActivationRateLimitRule.ACTIVATION_BY_USER, userB))
                .as("user khác phải có thùng riêng, không bị ảnh hưởng bởi userA")
                .isTrue();
    }

    @Test
    void userRuleAndIpRuleDoNotShareABucketForTheSameSubjectString() {
        // Cùng một chuỗi vẫn phải là hai thùng riêng vì bucketNamespace() có tiền tố tên quy tắc.
        String sameString = "shared-subject";
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryConsume(ActivationRateLimitRule.ACTIVATION_BY_USER, sameString)).isTrue();
        }
        assertThat(limiter.tryConsume(ActivationRateLimitRule.ACTIVATION_BY_USER, sameString)).isFalse();
        assertThat(limiter.tryConsume(ActivationRateLimitRule.ACTIVATION_BY_IP, sameString))
                .as("quy tắc IP phải còn nguyên hạn mức dù trùng chuỗi khoá với quy tắc user")
                .isTrue();
    }

    @Test
    void nullOrBlankSubjectIsAlwaysAllowedAndNeverBlocks() {
        assertThat(limiter.tryConsume(ActivationRateLimitRule.ACTIVATION_BY_IP, null)).isTrue();
        assertThat(limiter.tryConsume(ActivationRateLimitRule.ACTIVATION_BY_IP, "")).isTrue();
        assertThat(limiter.secondsUntilRefill(ActivationRateLimitRule.ACTIVATION_BY_IP, null)).isZero();
    }

    @Test
    void rejectIfLimitedReturnsNullWhenAllowedAndRetryAfterWhenBlocked() {
        String userId = "00000000-0000-7000-8000-00000000a003";
        for (int i = 0; i < 4; i++) {
            assertThat(limiter.rejectIfLimited(ActivationRateLimitRule.ACTIVATION_BY_USER, userId)).isNull();
        }
        // Lần thứ 5: vẫn còn trong hạn mức (đã dùng 4, hạn mức 5).
        assertThat(limiter.rejectIfLimited(ActivationRateLimitRule.ACTIVATION_BY_USER, userId)).isNull();

        ActivationRateLimiter.RetryAfter retryAfter =
                limiter.rejectIfLimited(ActivationRateLimitRule.ACTIVATION_BY_USER, userId);
        assertThat(retryAfter).isNotNull();
        assertThat(retryAfter.headerSeconds())
                .as("header Retry-After phải >= 1 giây")
                .isGreaterThanOrEqualTo(1L);
    }
}
