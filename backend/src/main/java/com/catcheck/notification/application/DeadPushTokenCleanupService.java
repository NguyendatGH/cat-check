package com.catcheck.notification.application;

import com.catcheck.notification.domain.port.PushSubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class DeadPushTokenCleanupService {

    static final Duration REVOKED_RETENTION = Duration.ofDays(30);

    static final Duration STALE_RETENTION = Duration.ofDays(180);

    static final double SAFETY_THRESHOLD_RATIO = 0.20;

    private final PushSubscriptionRepository repository;
    private final Clock clock;

    public DeadPushTokenCleanupService(PushSubscriptionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Survey survey(Instant now) {
        return new Survey(
                repository.countDead(now.minus(REVOKED_RETENTION), now.minus(STALE_RETENTION)),
                repository.countAll());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deleteBatch(Instant now, int limit) {
        return repository.deleteDead(
                now.minus(REVOKED_RETENTION), now.minus(STALE_RETENTION), limit);
    }

    public record Survey(long eligible, long total) {

        public boolean exceedsSafetyThreshold() {
            return total > 0 && eligible > 0 && (double) eligible / total > SAFETY_THRESHOLD_RATIO;
        }
    }
}
