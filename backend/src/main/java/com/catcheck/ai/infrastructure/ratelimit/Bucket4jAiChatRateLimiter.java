package com.catcheck.ai.infrastructure.ratelimit;

import com.catcheck.ai.domain.AiChatRateLimitRule;
import com.catcheck.ai.domain.port.AiChatRateLimiter;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class Bucket4jAiChatRateLimiter implements AiChatRateLimiter {

    private static final Duration BUCKET_TTL = Duration.ofHours(2);

    private static final int MAX_BUCKETS = 200_000;

    private final Cache<String, Bucket> buckets;
    private final Bandwidth bandwidth;

    public Bucket4jAiChatRateLimiter() {
        this.buckets = Caffeine.newBuilder()
                .maximumSize(MAX_BUCKETS)
                .expireAfterWrite(BUCKET_TTL)
                .build();
        this.bandwidth = Bandwidth.classic(20, Refill.intervally(20, Duration.ofHours(1)));
    }

    @Override
    public boolean tryConsume(AiChatRateLimitRule rule, String subject) {
        if (subject == null || subject.isBlank()) {
            return true;
        }
        return probe(rule, subject).isConsumed();
    }

    @Override
    public long secondsUntilRefill(AiChatRateLimitRule rule, String subject) {
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

    private ConsumptionProbe probe(AiChatRateLimitRule rule, String subject) {
        Bucket bucket = buckets.get(rule.bucketNamespace() + ' ' + subject,
                key -> Bucket.builder().addLimit(bandwidth).build());
        return bucket.tryConsumeAndReturnRemaining(1);
    }

    long liveBucketCount() {
        buckets.cleanUp();
        return buckets.estimatedSize();
    }
}
