package com.catcheck.identity.application.privacy;

import com.catcheck.identity.domain.SessionRevokeReason;
import com.catcheck.privacy.spi.GlobalSessionPurgePort;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Component
class PrivacySessionPurgeAdapter implements GlobalSessionPurgePort {

    private static final SessionRevokeReason REVOKE_REASON = SessionRevokeReason.ADMIN_LOCK;

    private final JdbcOperations jdbc;
    private final Clock clock;

    PrivacySessionPurgeAdapter(JdbcOperations jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PurgeOutcome purgeEverySession() {
        Instant now = clock.instant();
        int httpSessions = jdbc.update("DELETE FROM spring_session");
        int deviceSessions = jdbc.update("""
                UPDATE user_device_session
                   SET revoked_at = ?, revoke_reason = ?
                 WHERE revoked_at IS NULL
                """, now, REVOKE_REASON.name());
        int otp = jdbc.update("""
                UPDATE email_otp
                   SET consumed_at = COALESCE(consumed_at, ?),
                       ticket_expires_at = CASE WHEN ticket_hash IS NULL
                                                THEN ticket_expires_at ELSE ? END
                 WHERE consumed_at IS NULL
                    OR (ticket_hash IS NOT NULL
                        AND (ticket_expires_at IS NULL OR ticket_expires_at > ?))
                """, now, now, now);
        return new PurgeOutcome(httpSessions, deviceSessions, otp);
    }
}
