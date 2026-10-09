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

/**
 * {@link AiChatRateLimiter} trên Bucket4j + Caffeine trong RAM — cùng cơ chế mà
 * {@code credit.infrastructure.ratelimit.Bucket4jActivationRateLimiter} và
 * {@code shared.infrastructure.ratelimit.Bucket4jPublicIngressRateLimiter} đã dùng (p11 §11.7.1:
 * "backend Caffeine in-memory ở MVP"; khi &gt;1 instance đổi backend sang PostgreSQL/Redis,
 * <b>không đổi code nghiệp vụ</b>).
 *
 * <p><b>Caffeine, không phải {@code ConcurrentHashMap} trần</b>: khoá theo userId là do
 * ATTACKER CONTROL (tài khoản tự tạo được). Một map trần sẽ phình theo số userId bị dò/lạm dụng —
 * tự trao công cụ DoS cho kẻ tấn công. Caffeine dùng TTL để tự thu dọn.</p>
 *
 * <p>Chỉ MỘT {@link AiChatRateLimitRule} nên dùng một {@link Bandwidth} duy nhất thay vì
 * {@code EnumMap} như hai lớp chị em ở trên (hai quy tắc trở lên) — thêm quy tắc thứ hai sau này
 * thì đổi sang {@code EnumMap} theo đúng khuôn đó.</p>
 */
@Component
public class Bucket4jAiChatRateLimiter implements AiChatRateLimiter {

    /** Gấp đôi chu kỳ (1 giờ) để thùng không bị xoá giữa chừng. */
    private static final Duration BUCKET_TTL = Duration.ofHours(2);

    private static final int MAX_BUCKETS = 200_000;

    private final Cache<String, Bucket> buckets;
    private final Bandwidth bandwidth;

    public Bucket4jAiChatRateLimiter() {
        this.buckets = Caffeine.newBuilder()
                .maximumSize(MAX_BUCKETS)
                .expireAfterWrite(BUCKET_TTL)
                .build();
        // Số liệu ở đây PHẢI khớp AiChatRateLimitRule.documentedRate — xem javadoc của enum đó
        // để biết vì sao đây là lựa chọn tự chọn, không phải trích từ p11.
        this.bandwidth = Bandwidth.classic(20, Refill.intervally(20, Duration.ofHours(1)));
    }

    @Override
    public boolean tryConsume(AiChatRateLimitRule rule, String subject) {
        if (subject == null || subject.isBlank()) {
            // Không có khoá thì không đếm. Endpoint luôn yêu cầu đăng nhập (xem javadoc enum)
            // nên trong thực tế subject luôn có giá trị; giữ hành vi "cho qua" để nhất quán với
            // ActivationRateLimiter/PublicIngressRateLimiter chứ không phải để lách luật ở đây.
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
        // Làm tròn LÊN: trả 0 giây thì client thử lại ngay và bị chặn tiếp.
        return (nanos + Duration.ofSeconds(1).toNanos() - 1) / Duration.ofSeconds(1).toNanos();
    }

    private ConsumptionProbe probe(AiChatRateLimitRule rule, String subject) {
        Bucket bucket = buckets.get(rule.bucketNamespace() + ' ' + subject,
                key -> Bucket.builder().addLimit(bandwidth).build());
        return bucket.tryConsumeAndReturnRemaining(1);
    }

    /** Cho test: số thùng đang sống. KHÔNG dùng trong luồng nghiệp vụ. */
    long liveBucketCount() {
        buckets.cleanUp();
        return buckets.estimatedSize();
    }
}
