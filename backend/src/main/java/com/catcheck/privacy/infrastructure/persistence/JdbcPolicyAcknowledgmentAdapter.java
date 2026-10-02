package com.catcheck.privacy.infrastructure.persistence;

import com.catcheck.privacy.domain.PolicyAcknowledgment;
import com.catcheck.privacy.domain.PolicySurface;
import com.catcheck.privacy.domain.port.PolicyAcknowledgmentPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * {@link PolicyAcknowledgmentPort} trên {@code JdbcTemplate} — append-only (V6 REVOKE
 * UPDATE, DELETE). UNIQUE (user_id, policy_version_id, surface) đảm bảo một bản chỉ ghi
 * một lần cho một điểm chạm.
 */
@Repository
public class JdbcPolicyAcknowledgmentAdapter implements PolicyAcknowledgmentPort {

    private final JdbcTemplate jdbc;

    public JdbcPolicyAcknowledgmentAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(PolicyAcknowledgment acknowledgment) {
        jdbc.update("""
                INSERT INTO policy_acknowledgment (
                    id, user_id, policy_version_id, policy_hash, surface,
                    occurred_at, ip_address, user_agent, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?::inet, ?, ?)
                ON CONFLICT (user_id, policy_version_id, surface) DO NOTHING
                """,
                acknowledgment.id(),
                acknowledgment.userId(),
                acknowledgment.policyVersionId(),
                acknowledgment.policyHash(),
                acknowledgment.surface().name(),
                acknowledgment.occurredAt(),
                acknowledgment.ipAddress(),
                acknowledgment.userAgent(),
                acknowledgment.createdAt());
    }

    @Override
    public boolean exists(UUID userId, UUID policyVersionId, PolicySurface surface) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*)
                  FROM policy_acknowledgment
                 WHERE user_id = ? AND policy_version_id = ? AND surface = ?
                """, Integer.class, userId, policyVersionId, surface.name());
        return count != null && count > 0;
    }
}
