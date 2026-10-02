package com.catcheck.identity.domain.port;

import com.catcheck.identity.domain.DeviceSession;
import com.catcheck.identity.domain.SessionRevokeReason;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cong vao {@code user_device_session}. Trien khai o {@code ..infrastructure.persistence} (R7).
 */
public interface DeviceSessionRepository {

    List<DeviceSession> findActiveByUserId(UUID userId, Instant now);

    Optional<DeviceSession> findById(UUID sessionId);

    Optional<DeviceSession> findBySessionIdHash(String sessionIdHash);

    void insert(DeviceSession session);

    void touchLastSeen(UUID sessionId, Instant lastSeenAt);

    /** Tra ve {@code true} neu da thu hoi (idempotent) — phong tranh race khi bam nut 2 lan. */
    boolean revoke(UUID sessionId, Instant revokedAt, SessionRevokeReason reason);

    int revokeAllByUserId(UUID userId, Instant revokedAt, SessionRevokeReason reason);
}
