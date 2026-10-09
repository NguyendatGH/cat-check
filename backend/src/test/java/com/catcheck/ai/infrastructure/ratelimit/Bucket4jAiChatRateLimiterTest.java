package com.catcheck.ai.infrastructure.ratelimit;

import com.catcheck.ai.domain.AiChatRateLimitRule;
import com.catcheck.ai.domain.port.AiChatRateLimiter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Nhiệm vụ wiring A2-A4 ({@code docs/cat-rag-credit-pipeline.md} mục A4-(4)) — hạn mức gọi
 * {@code POST /api/v1/ai/chat}.
 *
 * <p>Ngưỡng theo {@link AiChatRateLimitRule}: 20/giờ theo userId (xem javadoc của enum đó cho lý
 * do chọn số — KHÔNG trích từ p11, {@code context/} đã xoá).</p>
 */
class Bucket4jAiChatRateLimiterTest {

    private final Bucket4jAiChatRateLimiter limiter = new Bucket4jAiChatRateLimiter();

    @Test
    void allowsExactlyTwentyCallsPerUserThenBlocksTheTwentyFirst() {
        String userId = "00000000-0000-7000-8000-00000000b001";

        for (int i = 0; i < 20; i++) {
            assertThat(limiter.tryConsume(AiChatRateLimitRule.AI_CHAT_BY_USER, userId))
                    .as("lần gọi thứ %d phải còn trong hạn mức", i + 1)
                    .isTrue();
        }
        assertThat(limiter.tryConsume(AiChatRateLimitRule.AI_CHAT_BY_USER, userId))
                .as("lần thứ 21 phải bị chặn")
                .isFalse();
        assertThat(limiter.secondsUntilRefill(AiChatRateLimitRule.AI_CHAT_BY_USER, userId))
                .as("phải báo số giây chờ > 0 khi đang bị chặn")
                .isPositive();
    }

    @Test
    void differentUsersGetIndependentBuckets() {
        String userA = "00000000-0000-7000-8000-00000000b001";
        String userB = "00000000-0000-7000-8000-00000000b002";

        for (int i = 0; i < 20; i++) {
            limiter.tryConsume(AiChatRateLimitRule.AI_CHAT_BY_USER, userA);
        }
        assertThat(limiter.tryConsume(AiChatRateLimitRule.AI_CHAT_BY_USER, userA)).isFalse();
        assertThat(limiter.tryConsume(AiChatRateLimitRule.AI_CHAT_BY_USER, userB))
                .as("user khác phải có thùng riêng, không bị ảnh hưởng bởi userA")
                .isTrue();
    }

    @Test
    void nullOrBlankSubjectIsAlwaysAllowedAndNeverBlocks() {
        assertThat(limiter.tryConsume(AiChatRateLimitRule.AI_CHAT_BY_USER, null)).isTrue();
        assertThat(limiter.tryConsume(AiChatRateLimitRule.AI_CHAT_BY_USER, "")).isTrue();
        assertThat(limiter.secondsUntilRefill(AiChatRateLimitRule.AI_CHAT_BY_USER, null)).isZero();
    }

    @Test
    void rejectIfLimitedReturnsNullWhenAllowedAndRetryAfterWhenBlocked() {
        String userId = "00000000-0000-7000-8000-00000000b003";
        for (int i = 0; i < 19; i++) {
            assertThat(limiter.rejectIfLimited(AiChatRateLimitRule.AI_CHAT_BY_USER, userId)).isNull();
        }
        // Lần thứ 20: vẫn còn trong hạn mức (đã dùng 19, hạn mức 20).
        assertThat(limiter.rejectIfLimited(AiChatRateLimitRule.AI_CHAT_BY_USER, userId)).isNull();

        AiChatRateLimiter.RetryAfter retryAfter =
                limiter.rejectIfLimited(AiChatRateLimitRule.AI_CHAT_BY_USER, userId);
        assertThat(retryAfter).isNotNull();
        assertThat(retryAfter.headerSeconds())
                .as("header Retry-After phải >= 1 giây")
                .isGreaterThanOrEqualTo(1L);
    }
}
