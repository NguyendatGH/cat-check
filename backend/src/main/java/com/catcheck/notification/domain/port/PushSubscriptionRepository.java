package com.catcheck.notification.domain.port;

import com.catcheck.notification.domain.PushRevokeReason;
import com.catcheck.notification.domain.PushSubscription;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushSubscriptionRepository {

    List<PushSubscription> findActiveByUser(UUID userId);

    long countActiveByUser(UUID userId);

    Optional<PushSubscription> findActiveByDeviceKey(UUID userId, String deviceKey);

    Optional<PushSubscription> findByIdForUser(UUID subscriptionId, UUID userId);

    PushSubscription upsert(PushSubscription subscription);

    int revokeInstallationIdOwnedByOtherUser(String installationId, UUID newOwnerId, Instant now);

    boolean revoke(UUID subscriptionId, UUID userId, PushRevokeReason reason, Instant now);

    void revokeById(UUID subscriptionId, PushRevokeReason reason, Instant now);

    void recordSuccess(UUID subscriptionId, Instant now);

    int recordFailure(UUID subscriptionId, Instant now);


    long countAll();

    long countDead(Instant revokedBefore, Instant lastSeenBefore);

    int deleteDead(Instant revokedBefore, Instant lastSeenBefore, int limit);
}

