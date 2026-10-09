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

/**
 * {@link ActivationRateLimiter} trên Bucket4j + Caffeine trong RAM — cùng cơ chế mà
 * {@code identity.infrastructure.ratelimit.Bucket4jRateLimiter} và
 * {@code shared.infrastructure.ratelimit.Bucket4jPublicIngressRateLimiter} đã dùng (p11 §11.7.1:
 * "backend Caffeine in-memory ở MVP"; khi &gt;1 instance đổi backend sang PostgreSQL/Redis,
 * <b>không đổi code nghiệp vụ</b>).
 *
 * <p><b>Vì sao Caffeine chứ không phải {@code ConcurrentHashMap} trần</b>: khoá theo userId/IP là
 * do ATTACKER CONTROL. Một map trần sẽ phình theo số userId/IP bị dò — chính ta trao công cụ DoS
 * cho kẻ tấn công. Caffeine dùng TTL để tự thu dọn.</p>
 *
 * <p>TTL = 2 lần chu kỳ dài nhất của quy tắc (1 giờ cho {@code ACTIVATION_BY_IP}), đủ để không xoá
 * sớm giữa chừng.</p>
 */
@Component
public class Bucket4jActivationRateLimiter implements ActivationRateLimiter {

    /** Gấp đôi chu kỳ lớn nhất (1 giờ) để thùng không bị xoá giữa chừng. */
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
            // Không có khoá thì không đếm cho quy tắc này. Endpoint vẫn được bảo vệ bởi quy tắc
            // còn lại (ví dụ thiếu userId thì vẫn còn quy tắc theo IP).
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
        // Làm tròn LÊN: trả 0 giây thì client thử lại ngay và bị chặn tiếp, làm tròn xuống cũng
        // vậy — giống Bucket4jRateLimiter của identity.
        return (nanos + Duration.ofSeconds(1).toNanos() - 1) / Duration.ofSeconds(1).toNanos();
    }

    private ConsumptionProbe probe(ActivationRateLimitRule rule, String subject) {
        Bucket bucket = buckets.get(rule.bucketNamespace() + ' ' + subject,
                key -> Bucket.builder().addLimit(bandwidths.get(rule)).build());
        return bucket.tryConsumeAndReturnRemaining(1);
    }

    private static Map<ActivationRateLimitRule, Bandwidth> buildBandwidths() {
        Map<ActivationRateLimitRule, Bandwidth> map = new EnumMap<>(ActivationRateLimitRule.class);
        // Số liệu ở đây PHẢI khớp ActivationRateLimitRule.documentedRate — xem javadoc của enum
        // đó để biết vì sao đây là lựa chọn tự chọn, không phải trích từ p11.
        map.put(ActivationRateLimitRule.ACTIVATION_BY_USER,
                Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(15))));
        map.put(ActivationRateLimitRule.ACTIVATION_BY_IP,
                Bandwidth.classic(20, Refill.intervally(20, Duration.ofHours(1))));
        return map;
    }

    /** Cho test: số thùng đang sống. KHÔNG dùng trong luồng nghiệp vụ. */
    long liveBucketCount() {
        buckets.cleanUp();
        return buckets.estimatedSize();
    }
}
