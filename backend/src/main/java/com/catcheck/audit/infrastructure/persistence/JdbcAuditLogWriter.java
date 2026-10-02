package com.catcheck.audit.infrastructure.persistence;

import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.application.AuditLogWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.Map;

/**
 * Ghi {@code audit_log} bang {@code JdbcTemplate}.
 *
 * <p>Khong dung JPA: {@code audit_log} chi co {@code INSERT}, khong bao gio
 * {@code UPDATE}/{@code DELETE} (p4 §4.6.2) nen khong co gi de mapping quan he.
 * Dung tay cung quyet dinh: mapping 1-1, kiem soat duoc cau lenh, va khong keo
 * Hibernate vao mot bang rat nhat.</p>
 *
 * <p>{@code occurred_at} de DB gan bang {@code DEFAULT now()} de su kien mang
 * thoi diem ghi server (R13 khong cho phep goi {@code Instant.now()}).</p>
 */
@Repository
public class JdbcAuditLogWriter implements AuditLogWriter {

    private static final String INSERT = """
            INSERT INTO audit_log (
                actor_type, actor_id, actor_role, subject_type, subject_user_id,
                action, result, metadata, before_data, after_data,
                request_id, ip_address, user_agent
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?::inet, ?)
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcAuditLogWriter(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void append(AuditEvent event, Map<String, Object> redactedMetadata,
                       Map<String, Object> redactedBefore, Map<String, Object> redactedAfter) {
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(INSERT);
            int i = 1;
            ps.setString(i++, event.actor().type().name());
            setUuid(ps, i++, event.actor().userId());
            ps.setString(i++, event.actor().role());
            ps.setString(i++, event.subjectType().name());
            setUuid(ps, i++, event.subjectUserId());
            ps.setString(i++, event.action());
            ps.setString(i++, event.outcome().name());
            ps.setString(i++, toJson(redactedMetadata));
            ps.setString(i++, toJson(redactedBefore));
            ps.setString(i++, toJson(redactedAfter));
            ps.setString(i++, event.requestId());
            // ?::inet: pgjdbc gui chuoi nhu kieu `unknown` de PostgreSQL tu suy nen
            // khong can parse dia chi IP trong Java (va khong can bao loi khi client
            // gui gia tri khong hop le sau hau tang proxy).
            if (event.ipAddress() == null) {
                ps.setNull(i++, Types.VARCHAR);
            } else {
                ps.setString(i++, event.ipAddress());
            }
            ps.setString(i, event.userAgent());
            return ps;
        });
    }

    private void setUuid(PreparedStatement ps, int index, java.util.UUID value) throws java.sql.SQLException {
        if (value == null) {
            ps.setNull(index, Types.OTHER);
        } else {
            ps.setObject(index, value);
        }
    }

    /**
     * {@code metadata} bao gio la JSON object (DB co default {@code {}}), con
     * {@code before}/{@code after} la NULL neu khong doi — phai phan biet
     * {@code null} voi {@code {}} o day.
     */
    private String toJson(Map<String, Object> value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            // Khong nem: ghi audit khong duoc lam do mat hanh dong nghiep vu. Thay
            // bang object rong de dong van khong vo du lieu va vi co the do la
            // value khong serial hoa duoc (vd. con tro).
            return "{}";
        }
    }
}
