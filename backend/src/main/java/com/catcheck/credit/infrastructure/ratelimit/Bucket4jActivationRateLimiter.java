package com.catcheck.credit.infrastructure.ratelimit;

import com.catcheck.credit.domain.ActivationRateLimitRule;
import com.catcheck.credit.domain.port.ActivationRateLimiter;
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
public class Bucket4jActivationRateLimiter implements ActivationRateLimiter {

    private static final Duration BUCKET_TTL = Duration.ofHours(2);

    private static final int MAX_BUCKETS = 200_000;

    private final Cache<String, Bucket> buckets;
    private final Map<ActivationRateLimitRule, Bandwidth> bandwidths;

    public Bucket4jActivationRateLimiter() {
        this.buckets = Caffeine.newBuilder()
                .maximumSize(MAX_BUCKETS)
                .expireAfterWrite(BUCKET_TTL)
                .build();
        this.bandwidths = buildBandwidths();
    }

    @Override
    public boolean tryConsume(ActivationRateLimitRule rule, String subject) {
        if (subject == null || subject.isBlank()) {
            return true;
        }
        return probe(rule, subject).isConsumed();
    }

    @Override
    public long secondsUntilRefill(ActivationRateLimitRule rule, String subject) {
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

    private ConsumptionProbe probe(ActivationRateLimitRule rule, String subject) {
        Bucket bucket = buckets.get(rule.bucketNamespace() + ' ' + subject,
                key -> Bucket.builder().addLimit(bandwidths.get(rule)).build());
        return bucket.tryConsumeAndReturnRemaining(1);
    }

    private static Map<ActivationRateLimitRule, Bandwidth> buildBandwidths() {
        Map<ActivationRateLimitRule, Bandwidth> map = new EnumMap<>(ActivationRateLimitRule.class);
        map.put(ActivationRateLimitRule.ACTIVATION_BY_USER,
                Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(15))));
        map.put(ActivationRateLimitRule.ACTIVATION_BY_IP,
                Bandwidth.classic(20, Refill.intervally(20, Duration.ofHours(1))));
        return map;
    }

    long liveBucketCount() {
        buckets.cleanUp();
        return buckets.estimatedSize();
    }
}
