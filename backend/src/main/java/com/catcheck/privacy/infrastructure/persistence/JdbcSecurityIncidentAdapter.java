package com.catcheck.privacy.infrastructure.persistence;

import com.catcheck.privacy.domain.IncidentCategory;
import com.catcheck.privacy.domain.IncidentSeverity;
import com.catcheck.privacy.domain.SecurityIncident;
import com.catcheck.privacy.domain.port.SecurityIncidentPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@link SecurityIncidentPort} trên {@code JdbcTemplate}. Bảng bị REVOKE DELETE (V6) nhưng
 * UPDATE được — adapter có save + update, không có delete.
 */
@Repository
public class JdbcSecurityIncidentAdapter implements SecurityIncidentPort {

    private static final String INSERT = """
            INSERT INTO security_incident (
                id, public_ref, severity, category, summary, affected_subject_count,
                affected_data_codes, detected_at, classified_at, contained_at,
                authority_notified_at, subjects_notified_at, resolved_at, retain_until,
                handled_by, report_ref, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?::varchar[], ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String COLUMNS = """
            id, public_ref, severity, category, summary, affected_subject_count,
            affected_data_codes, detected_at, classified_at, contained_at,
            authority_notified_at, subjects_notified_at, resolved_at, retain_until,
            handled_by, report_ref, created_at
            """;

    private final JdbcTemplate jdbc;

    public JdbcSecurityIncidentAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SecurityIncident save(SecurityIncident incident) {
        String publicRef = nextPublicRef();
        jdbc.update(INSERT,
                incident.id(),
                publicRef,
                incident.severity().name(),
                incident.category().name(),
                incident.summary(),
                incident.affectedSubjectCount(),
                incident.affectedDataCodes().toArray(new String[0]),
                incident.detectedAt(),
                incident.classifiedAt(),
                incident.containedAt(),
                incident.authorityNotifiedAt(),
                incident.subjectsNotifiedAt(),
                incident.resolvedAt(),
                incident.retainUntil(),
                incident.handledBy(),
                incident.reportRef(),
                incident.createdAt());
        return new SecurityIncident(incident.id(), publicRef, incident.severity(), incident.category(),
                incident.summary(), incident.affectedSubjectCount(), incident.affectedDataCodes(),
                incident.detectedAt(), incident.classifiedAt(), incident.containedAt(),
                incident.authorityNotifiedAt(), incident.subjectsNotifiedAt(), incident.resolvedAt(),
                incident.retainUntil(), incident.handledBy(), incident.reportRef(), incident.createdAt());
    }

    @Override
    public String nextPublicRef() {
        return jdbc.queryForObject("""
                SELECT 'INC-' || to_char(now(), 'YYYY') || '-'
                       || lpad(nextval('security_incident_public_ref_seq')::text, 6, '0')
                """, String.class);
    }

    @Override
    public void update(SecurityIncident incident) {
        jdbc.update("""
                UPDATE security_incident
                   SET severity = ?, category = ?, summary = ?, affected_subject_count = ?,
                       affected_data_codes = ?::varchar[], classified_at = ?, contained_at = ?,
                       authority_notified_at = ?, subjects_notified_at = ?, resolved_at = ?,
                       retain_until = ?, handled_by = ?, report_ref = ?
                 WHERE id = ?
                """,
                incident.severity().name(),
                incident.category().name(),
                incident.summary(),
                incident.affectedSubjectCount(),
                incident.affectedDataCodes().toArray(new String[0]),
                incident.classifiedAt(),
                incident.containedAt(),
                incident.authorityNotifiedAt(),
                incident.subjectsNotifiedAt(),
                incident.resolvedAt(),
                incident.retainUntil(),
                incident.handledBy(),
                incident.reportRef(),
                incident.id());
    }

    @Override
    public List<SecurityIncident> findOverdueAuthorityNotification(Instant now) {
        return jdbc.query("""
                SELECT\s""" + COLUMNS + """
                  FROM security_incident
                 WHERE severity IN ('HIGH', 'CRITICAL')
                   AND authority_notified_at IS NULL
                   AND detected_at < ?
                 ORDER BY detected_at
                """, (rs, rowNum) -> readIncident(rs), now.minusSeconds(72 * 3600));
    }

    private SecurityIncident readIncident(ResultSet rs) {
        try {
            return new SecurityIncident(
                    RowReaders.uuid(rs, "id"),
                    RowReaders.requiredString(rs, "public_ref"),
                    IncidentSeverity.valueOf(rs.getString("severity")),
                    IncidentCategory.valueOf(rs.getString("category")),
                    RowReaders.requiredString(rs, "summary"),
                    RowReaders.boxedInt(rs, "affected_subject_count"),
                    RowReaders.stringList(rs, "affected_data_codes"),
                    RowReaders.requiredInstant(rs, "detected_at"),
                    RowReaders.instant(rs, "classified_at"),
                    RowReaders.instant(rs, "contained_at"),
                    RowReaders.instant(rs, "authority_notified_at"),
                    RowReaders.instant(rs, "subjects_notified_at"),
                    RowReaders.instant(rs, "resolved_at"),
                    RowReaders.instant(rs, "retain_until"),
                    RowReaders.uuid(rs, "handled_by"),
                    RowReaders.string(rs, "report_ref"),
                    RowReaders.requiredInstant(rs, "created_at"));
        } catch (SQLException ex) {
            throw new IllegalStateException("Đọc security_incident thất bại", ex);
        }
    }
}
