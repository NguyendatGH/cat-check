package com.catcheck.identity.domain.port;

import com.catcheck.identity.domain.DeviceSession;
import com.catcheck.identity.domain.SessionRevokeReason;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceSessionRepository {

    List<DeviceSession> findActiveByUserId(UUID userId, Instant now);

    Optional<DeviceSession> findById(UUID sessionId);

    Optional<DeviceSession> findBySessionIdHash(String sessionIdHash);

    void insert(DeviceSession session);

    void touchLastSeen(UUID sessionId, Instant lastSeenAt);

    boolean revoke(UUID sessionId, Instant revokedAt, SessionRevokeReason reason);

    int revokeAllByUserId(UUID userId, Instant revokedAt, SessionRevokeReason reason);
}
