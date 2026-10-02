package com.catcheck.privacy.infrastructure.persistence;

import com.catcheck.privacy.domain.ConsentMethod;
import com.catcheck.privacy.domain.ConsentRecord;
import com.catcheck.privacy.domain.ConsentStatus;
import com.catcheck.privacy.domain.port.ConsentRecordPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link ConsentRecordPort} trên {@code JdbcTemplate}.
 *
 * <p>Bảng append-only (bất biến I16): adapter CHỈ có INSERT + SELECT, không có
 * UPDATE/DELETE — V6 đã REVOKE quyền đó khỏi {@code catcheck_app}. Rút đồng ý = INSERT
 * dòng {@code WITHDRAWN} + {@code supersedes_id} (p15 §15.3.5).</p>
 *
 * <p>Trạng thái hiện hành đọc qua view {@code consent_current} (DISTINCT ON — p4 B2),
 * KHÔNG đọc rồi tự tính trong code: view là nguồn sự thật duy nhất, lệch ở đây là lệch
 * bằng chứng pháp lý.</p>
 */
@Repository
public class JdbcConsentRecordAdapter implements ConsentRecordPort {

    private static final String INSERT = """
            INSERT INTO consent_record (
                id, user_id, purpose_code, status, policy_version_id, policy_hash,
                consent_text_hash, method, ui_surface, locale, supersedes_id,
                occurred_at, ip_address, user_agent, request_id, evidence, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::inet, ?, ?, ?::jsonb, ?)
            """;

    private static final String COLUMNS = """
            id, user_id, purpose_code, status, policy_version_id, policy_hash,
            consent_text_hash, method, ui_surface, locale, supersedes_id,
            occurred_at, ip_address, user_agent, request_id, evidence, created_at
            """;

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcConsentRecordAdapter(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(ConsentRecord record) {
        String evidence = null;
        if (record.evidence() != null && !record.evidence().isEmpty()) {
            try {
                evidence = objectMapper.writeValueAsString(record.evidence());
            } catch (JacksonException ex) {
                throw new IllegalStateException("Ghi evidence của consent_record thất bại", ex);
            }
        }
        jdbc.update(INSERT,
                record.id(),
                record.userId(),
                record.purposeCode(),
                record.status().name(),
                record.policyVersionId(),
                record.policyHash(),
                record.consentTextHash(),
                record.method().name(),
                record.uiSurface(),
                record.locale(),
                record.supersedesId(),
                record.occurredAt(),
                record.ipAddress(),
                record.userAgent(),
                record.requestId(),
                evidence,
                record.createdAt());
    }

    @Override
    public Optional<ConsentRecord> findLatest(UUID userId, String purposeCode) {
        return jdbc.query("""
                SELECT\s""" + COLUMNS + """
                  FROM consent_current
                 WHERE user_id = ? AND purpose_code = ?
                """, (rs, rowNum) -> readRecord(rs), userId, purposeCode).stream().findFirst();
    }

    @Override
    public ConsentStatus findCurrentStatus(UUID userId, String purposeCode) {
        List<String> statuses = jdbc.query("""
                SELECT status
                  FROM consent_current
                 WHERE user_id = ? AND purpose_code = ?
                """, (rs, rowNum) -> rs.getString("status"), userId, purposeCode);
        return statuses.isEmpty() ? null : ConsentStatus.valueOf(statuses.get(0));
    }

    @Override
    public List<ConsentRecord> findHistoryByUser(UUID userId, Instant before, int limit) {
        return jdbc.query("""
                SELECT\s""" + COLUMNS + """
                  FROM consent_record
                 WHERE user_id = ? AND occurred_at < ?
                 ORDER BY occurred_at DESC
                 LIMIT ?
                """, (rs, rowNum) -> readRecord(rs), userId, before, limit);
    }

    @Override
    public Optional<ConsentRecord> findById(UUID id) {
        return jdbc.query("""
                SELECT\s""" + COLUMNS + """
                  FROM consent_record
                 WHERE id = ?
                """, (rs, rowNum) -> readRecord(rs), id).stream().findFirst();
    }

    private ConsentRecord readRecord(ResultSet rs) {
        try {
            Map<String, Object> evidence = null;
            String evidenceJson = rs.getString("evidence");
            if (evidenceJson != null && !evidenceJson.isBlank()) {
                evidence = objectMapper.readValue(evidenceJson, MAP_TYPE);
            }
            return new ConsentRecord(
                    RowReaders.uuid(rs, "id"),
                    RowReaders.uuid(rs, "user_id"),
                    RowReaders.requiredString(rs, "purpose_code"),
                    ConsentStatus.valueOf(rs.getString("status")),
                    RowReaders.uuid(rs, "policy_version_id"),
                    RowReaders.requiredString(rs, "policy_hash"),
                    RowReaders.requiredString(rs, "consent_text_hash"),
                    ConsentMethod.valueOf(rs.getString("method")),
                    RowReaders.string(rs, "ui_surface"),
                    RowReaders.requiredString(rs, "locale"),
                    RowReaders.uuid(rs, "supersedes_id"),
                    RowReaders.requiredInstant(rs, "occurred_at"),
                    rs.getString("ip_address"),
                    RowReaders.string(rs, "user_agent"),
                    RowReaders.string(rs, "request_id"),
                    evidence,
                    RowReaders.requiredInstant(rs, "created_at"));
        } catch (SQLException | JacksonException ex) {
            throw new IllegalStateException("Đọc consent_record thất bại", ex);
        }
    }
}
