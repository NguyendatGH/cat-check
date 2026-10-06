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

/**
 * {@link PublicIngressRateLimiter} tren Bucket4j + Caffeine trong RAM — dung co che ma
 * p11 §11.7.1 chot cho MVP ("backend Caffeine in-memory o MVP").
 *
 * <p><b>Caffeine, khong phai {@code ConcurrentHashMap}:</b> khoa la IP do client quyet dinh,
 * nen mot map tho se phinh theo so IP nguon — tu trao cong cu DoS cho ke tan cong, va o dung
 * hai endpoint cong khai nhat cua he thong. TTL tu thu don; {@code maximumSize} la tran cung
 * thu hai.</p>
 *
 * <p>{@code Refill.intervally} (cua so co dinh) chu khong {@code greedy}: "60/phut" phai nghia
 * la 60 suot mot phut. Voi {@code greedy}, sau khi nhac duoc mot token thi client gom lai duoc
 * ca cum — tuc la 60 request trong mot giay van hop le, dung dieu han muc nay sinh ra de
 * chan.</p>
 */
@Component
public class Bucket4jPublicIngressRateLimiter implements PublicIngressRateLimiter {

    /** Gap doi cua so dai nhat (1 phut) — du de khong xoa thung giua chung. */
    private static final Duration BUCKET_TTL = Duration.ofMinutes(5);

    /** Tran cung: ~50k IP dang theo doi la du cho mot instance MVP. */
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
        // Lam tron LEN: tra 0 giay thi client thu lai ngay va bi chan tiep, con
        // lam tron xuong cung vay. Giong Bucket4jRateLimiter cua identity.
        return (nanos + Duration.ofSeconds(1).toNanos() - 1) / Duration.ofSeconds(1).toNanos();
    }

    /**
     * {@code limit.name()} di vao khoa nen hai endpoint khong chia se thung: mot trang web bi
     * loi CSP lien tuc khong duoc lam cho bao cao loi runtime (L73) cua chinh may do bi chan.
     */
    private ConsumptionProbe probe(Limit limit, String clientIp) {
        Bucket bucket = buckets.get(limit.name() + ' ' + clientIp,
                key -> Bucket.builder().addLimit(bandwidths.get(limit)).build());
        return bucket.tryConsumeAndReturnRemaining(1);
    }

    /** Cho test: so thung dang song. KHONG dung trong luong nghiep vu. */
    long liveBucketCount() {
        buckets.cleanUp();
        return buckets.estimatedSize();
    }
}
