package com.catcheck.identity.domain.port;

import java.util.Set;
import java.util.UUID;

public interface AuthenticatedSessionRevoker {

    int revokeAllByUserId(UUID userId);

    void revokeBySessionId(String sessionId);

    Set<String> activeSessionIdsByUserId(UUID userId);
}
