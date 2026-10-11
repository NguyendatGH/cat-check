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

/**
 * {@link CommunityRateLimiter} trên Bucket4j + Caffeine trong RAM — cùng cơ chế mà
 * {@code credit.infrastructure.ratelimit.Bucket4jActivationRateLimiter} và
 * {@code identity.infrastructure.ratelimit.Bucket4jRateLimiter} đã dùng (MVP, một instance; khi
 * scale ra nhiều instance thì đổi backend sang PostgreSQL/Redis, không đổi code nghiệp vụ).
 *
 * <p><b>Vì sao Caffeine chứ không phải {@code ConcurrentHashMap} trần</b>: khoá theo userId là do
 * ATTACKER CONTROL (bất kỳ ai tạo được tài khoản đều sinh ra khoá mới). Một map trần sẽ phình vô
 * hạn theo số tài khoản bị dùng để dò, trao công cụ DoS cho kẻ tấn công. Caffeine dùng TTL để tự
 * thu dọn.</p>
 */
@Component
public class Bucket4jCommunityRateLimiter implements CommunityRateLimiter {

    /** Gấp đôi chu kỳ dài nhất (10 phút) để thùng không bị xoá giữa chừng. */
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
        // Làm tròn LÊN: trả 0 giây thì client thử lại ngay và bị chặn tiếp.
        return (nanos + Duration.ofSeconds(1).toNanos() - 1) / Duration.ofSeconds(1).toNanos();
    }

    private ConsumptionProbe probe(CommunityRateLimitRule rule, String subject) {
        Bucket bucket = buckets.get(rule.bucketNamespace() + ' ' + subject,
                key -> Bucket.builder().addLimit(bandwidths.get(rule)).build());
        return bucket.tryConsumeAndReturnRemaining(1);
    }

    private static Map<CommunityRateLimitRule, Bandwidth> buildBandwidths() {
        Map<CommunityRateLimitRule, Bandwidth> map = new EnumMap<>(CommunityRateLimitRule.class);
        // Số liệu ở đây PHẢI khớp CommunityRateLimitRule.documentedRate — xem javadoc của enum đó
        // để biết vì sao đây là lựa chọn tự chọn, không phải trích từ spec.
        map.put(CommunityRateLimitRule.POST_CREATE_BY_USER,
                Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(10))));
        map.put(CommunityRateLimitRule.COMMENT_CREATE_BY_USER,
                Bandwidth.classic(20, Refill.intervally(20, Duration.ofMinutes(10))));
        map.put(CommunityRateLimitRule.REPORT_CREATE_BY_USER,
                Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(10))));
        return map;
    }

    /** Cho test: số thùng đang sống. KHÔNG dùng trong luồng nghiệp vụ. */
    long liveBucketCount() {
        buckets.cleanUp();
        return buckets.estimatedSize();
    }
}
