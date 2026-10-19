package com.catcheck.shared.infrastructure.ratelimit;

import com.catcheck.shared.application.spi.PublicIngressRateLimiter;
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
public class Bucket4jPublicIngressRateLimiter implements PublicIngressRateLimiter {

    private static final Duration BUCKET_TTL = Duration.ofMinutes(5);

    private static final int MAX_BUCKETS = 50_000;

    private final Cache<String, Bucket> buckets;
    private final Map<Limit, Bandwidth> bandwidths;

    public Bucket4jPublicIngressRateLimiter() {
        this.buckets = Caffeine.newBuilder()
                .maximumSize(MAX_BUCKETS)
                .expireAfterWrite(BUCKET_TTL)
                .build();
        this.bandwidths = new EnumMap<>(Limit.class);
        for (Limit limit : Limit.values()) {
            this.bandwidths.put(limit, Bandwidth.classic(limit.perMinute(),
                    Refill.intervally(limit.perMinute(), Duration.ofMinutes(1))));
        }
    }

    @Override
    public boolean tryConsume(Limit limit, String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return true;
        }
        return probe(limit, clientIp).isConsumed();
    }

    @Override
    public long secondsUntilRefill(Limit limit, String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return 0;
        }
        ConsumptionProbe probe = probe(limit, clientIp);
        if (probe.isConsumed()) {
            return 0;
        }
        long nanos = probe.getNanosToWaitForRefill();
        return (nanos + Duration.ofSeconds(1).toNanos() - 1) / Duration.ofSeconds(1).toNanos();
    }

    private ConsumptionProbe probe(Limit limit, String clientIp) {
        Bucket bucket = buckets.get(limit.name() + ' ' + clientIp,
                key -> Bucket.builder().addLimit(bandwidths.get(limit)).build());
        return bucket.tryConsumeAndReturnRemaining(1);
    }

    long liveBucketCount() {
        buckets.cleanUp();
        return buckets.estimatedSize();
    }
}
