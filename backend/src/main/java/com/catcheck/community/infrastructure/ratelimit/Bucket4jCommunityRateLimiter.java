package com.catcheck.community.infrastructure.ratelimit;

import com.catcheck.community.domain.CommunityRateLimitRule;
import com.catcheck.community.domain.port.CommunityRateLimiter;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

@Component
public class Bucket4jCommunityRateLimiter implements CommunityRateLimiter {

    private static final Duration BUCKET_TTL = Duration.ofMinutes(20);

    private static final int MAX_BUCKETS = 200_000;

    private final Cache<String, Bucket> buckets;
    private final Map<CommunityRateLimitRule, Bandwidth> bandwidths;

    public Bucket4jCommunityRateLimiter() {
        this.buckets = Caffeine.newBuilder()
                .maximumSize(MAX_BUCKETS)
                .expireAfterWrite(BUCKET_TTL)
                .build();
        this.bandwidths = buildBandwidths();
    }

    @Override
    public boolean tryConsume(CommunityRateLimitRule rule, String subject) {
        if (subject == null || subject.isBlank()) {
            return true;
        }
        return probe(rule, subject).isConsumed();
    }

    @Override
    public long secondsUntilRefill(CommunityRateLimitRule rule, String subject) {
        if (subject == null || subject.isBlank()) {
            return 0;
        }
        ConsumptionProbe probe = probe(rule, subject);
        if (probe.isConsumed()) {
            return 0;
        }
        long nanos = probe.getNanosToWaitForRefill();
        return (nanos + Duration.ofSeconds(1).toNanos() - 1) / Duration.ofSeconds(1).toNanos();
    }

    private ConsumptionProbe probe(CommunityRateLimitRule rule, String subject) {
        Bucket bucket = buckets.get(rule.bucketNamespace() + ' ' + subject,
                key -> Bucket.builder().addLimit(bandwidths.get(rule)).build());
        return bucket.tryConsumeAndReturnRemaining(1);
    }

    private static Map<CommunityRateLimitRule, Bandwidth> buildBandwidths() {
        Map<CommunityRateLimitRule, Bandwidth> map = new EnumMap<>(CommunityRateLimitRule.class);
        map.put(CommunityRateLimitRule.POST_CREATE_BY_USER,
                Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(10))));
        map.put(CommunityRateLimitRule.COMMENT_CREATE_BY_USER,
                Bandwidth.classic(20, Refill.intervally(20, Duration.ofMinutes(10))));
        map.put(CommunityRateLimitRule.REPORT_CREATE_BY_USER,
                Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(10))));
        return map;
    }

    long liveBucketCount() {
        buckets.cleanUp();
        return buckets.estimatedSize();
    }
}
