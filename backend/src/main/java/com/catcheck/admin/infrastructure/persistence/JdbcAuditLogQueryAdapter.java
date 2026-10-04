package com.catcheck.admin.infrastructure.persistence;

import com.catcheck.admin.domain.AuditLogRow;
import com.catcheck.admin.domain.port.AuditLogQueryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.time.OffsetDateTime;

@Repository
public class JdbcAuditLogQueryAdapter implements AuditLogQueryPort {

    private final JdbcTemplate jdbc;

    public JdbcAuditLogQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<AuditLogRow> find(String action, String result, String actorType, UUID actorId, int offset, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, occurred_at, actor_type, actor_role, subject_type, action, result,
                       request_id, metadata::text AS metadata
                FROM audit_log
                WHERE 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        appendFilter(sql, args, "action", action);
        appendFilter(sql, args, "result", result);
        appendFilter(sql, args, "actor_type", actorType);
        if (actorId != null) {
            sql.append(" AND actor_id = ?");
            args.add(actorId);
        }
        sql.append(" ORDER BY occurred_at DESC, id DESC OFFSET ? LIMIT ?");
        args.add(offset);
        args.add(limit);
        return jdbc.query(sql.toString(), (rs, rowNum) -> new AuditLogRow(
                rs.getObject("id", UUID.class), rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                rs.getString("actor_type"), rs.getString("actor_role"), rs.getString("subject_type"),
                rs.getString("action"), rs.getString("result"), rs.getString("request_id"),
                rs.getString("metadata")), args.toArray());
    }

    @Override
    public long count(String action, String result, String actorType, UUID actorId) {
        StringBuilder sql = new StringBuilder("SELECT count(*) FROM audit_log WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        appendFilter(sql, args, "action", action);
        appendFilter(sql, args, "result", result);
        appendFilter(sql, args, "actor_type", actorType);
        if (actorId != null) {
            sql.append(" AND actor_id = ?");
            args.add(actorId);
        }
        Long count = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    private static void appendFilter(StringBuilder sql, List<Object> args, String column, String value) {
        if (value != null && !value.isBlank()) {
            sql.append(" AND ").append(column).append(" = ?");
            args.add(value);
        }
    }
}
