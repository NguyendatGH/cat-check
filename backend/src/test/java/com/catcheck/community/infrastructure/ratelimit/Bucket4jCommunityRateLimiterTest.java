package com.catcheck.community.infrastructure.ratelimit;

import com.catcheck.community.domain.CommunityRateLimitRule;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ba luồng ghi của community (đăng bài, bình luận, report) — ngưỡng theo
 * {@link CommunityRateLimitRule} (5/20/10 trong 10 phút, tự chọn vì không còn spec).
 */
class Bucket4jCommunityRateLimiterTest {

    @ParameterizedTest
    @CsvSource({"POST_CREATE_BY_USER,5", "COMMENT_CREATE_BY_USER,20", "REPORT_CREATE_BY_USER,10"})
    void eachRuleAllowsItsQuotaPerUserThenBlocks(CommunityRateLimitRule rule, int quota) {
        Bucket4jCommunityRateLimiter limiter = new Bucket4jCommunityRateLimiter();
        String userA = "00000000-0000-7000-8000-00000000c001";
        String userB = "00000000-0000-7000-8000-00000000c002";

        for (int i = 0; i < quota; i++) {
            assertThat(limiter.tryConsume(rule, userA)).as("lần %d của %s", i + 1, rule).isTrue();
        }
        assertThat(limiter.tryConsume(rule, userA)).as("lần %d phải bị chặn", quota + 1).isFalse();
        assertThat(limiter.secondsUntilRefill(rule, userA)).isBetween(1L, 600L);
        assertThat(limiter.rejectIfLimited(rule, userA)).isNotNull();

        assertThat(limiter.tryConsume(rule, userB)).as("user khác có thùng riêng").isTrue();
        for (CommunityRateLimitRule other : CommunityRateLimitRule.values()) {
            if (other != rule) {
                assertThat(limiter.tryConsume(other, userA)).as("%s không chung thùng với %s", other, rule).isTrue();
            }
        }
    }
}
