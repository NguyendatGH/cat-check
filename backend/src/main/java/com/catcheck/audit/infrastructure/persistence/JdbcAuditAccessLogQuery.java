package com.catcheck.audit.infrastructure.persistence;

import com.catcheck.audit.api.AuditAccessLogQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
class JdbcAuditAccessLogQuery implements AuditAccessLogQuery {

    private final JdbcTemplate jdbc;

    JdbcAuditAccessLogQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Entry> accessesToUserData(UUID subjectUserId, int limit) {
        return jdbc.query("""
                SELECT occurred_at, actor_type, actor_role, action, result
                  FROM audit_log
                 WHERE subject_user_id = ? AND actor_type IN ('ADMIN', 'DPO')
                 ORDER BY occurred_at DESC, id DESC
                 LIMIT ?
                """,
                (rs, rowNum) -> new Entry(
                        rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                        rs.getString("actor_type"),
                        rs.getString("actor_role"),
                        rs.getString("action"),
                        rs.getString("result")),
                subjectUserId, limit);
    }
}
