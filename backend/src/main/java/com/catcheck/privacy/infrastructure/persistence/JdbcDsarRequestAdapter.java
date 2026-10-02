package com.catcheck.privacy.infrastructure.persistence;

import com.catcheck.privacy.domain.DsarChannel;
import com.catcheck.privacy.domain.DsarRequest;
import com.catcheck.privacy.domain.DsarRequestType;
import com.catcheck.privacy.domain.DsarStatus;
import com.catcheck.privacy.domain.port.DsarRequestPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link DsarRequestPort} trên {@code JdbcTemplate}.
 *
 * <p>{@code public_ref} sinh bằng sequence {@code dsar_request_public_ref_seq} của V6:
 * {@code 'DSAR-' || to_char(now(),'YYYY') || '-' || lpad(nextval(...)::text, 6, '0')}.
 * Gọi nextval TRONG transaction của save để không hụt số khi rollback.</p>
 */
@Repository
public class JdbcDsarRequestAdapter implements DsarRequestPort {

    private static final String INSERT = """
            INSERT INTO dsar_request (
                id, public_ref, user_id, contact_email, request_type, channel, status,
                identity_verified_at, identity_method, received_at, ack_due_at, ack_sent_at,
                fulfil_due_at, extended_to, extension_reason, third_party_involved,
                completed_at, rejection_reason, result_ref, result_expires_at,
                result_downloaded_at, handled_by, pseudonym_id, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String COLUMNS = """
            id, public_ref, user_id, contact_email, request_type, channel, status,
            identity_verified_at, identity_method, received_at, ack_due_at, ack_sent_at,
            fulfil_due_at, extended_to, extension_reason, third_party_involved,
            completed_at, rejection_reason, result_ref, result_expires_at,
            result_downloaded_at, handled_by, pseudonym_id, created_at
            """;

    private final JdbcTemplate jdbc;

    public JdbcDsarRequestAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public DsarRequest save(DsarRequest request) {
        String publicRef = nextPublicRef();
        jdbc.update(INSERT,
                request.id(),
                publicRef,
                request.userId(),
                request.contactEmail(),
                request.requestType().name(),
                request.channel().name(),
                request.status().name(),
                request.identityVerifiedAt(),
                request.identityMethod(),
                request.receivedAt(),
                request.ackDueAt(),
                request.ackSentAt(),
                request.fulfilDueAt(),
                request.extendedTo(),
                request.extensionReason(),
                request.thirdPartyInvolved(),
                request.completedAt(),
                request.rejectionReason(),
                request.resultRef(),
                request.resultExpiresAt(),
                request.resultDownloadedAt(),
                request.handledBy(),
                request.pseudonymId(),
                request.createdAt());
        return new DsarRequest(request.id(), publicRef, request.userId(), request.contactEmail(),
                request.requestType(), request.channel(), request.status(), request.identityVerifiedAt(),
                request.identityMethod(), request.receivedAt(), request.ackDueAt(), request.ackSentAt(),
                request.fulfilDueAt(), request.extendedTo(), request.extensionReason(),
                request.thirdPartyInvolved(), request.completedAt(), request.rejectionReason(),
                request.resultRef(), request.resultExpiresAt(), request.resultDownloadedAt(),
                request.handledBy(), request.pseudonymId(), request.createdAt());
    }

    @Override
    public String nextPublicRef() {
        return jdbc.queryForObject("""
                SELECT 'DSAR-' || to_char(now(), 'YYYY') || '-'
                       || lpad(nextval('dsar_request_public_ref_seq')::text, 6, '0')
                """, String.class);
    }

    @Override
    public void update(DsarRequest request) {
        jdbc.update("""
                UPDATE dsar_request
                   SET status = ?, ack_sent_at = ?, extended_to = ?, extension_reason = ?,
                       completed_at = ?, rejection_reason = ?, result_ref = ?,
                       result_expires_at = ?, result_downloaded_at = ?, handled_by = ?,
                       pseudonym_id = ?
                 WHERE id = ?
                """,
                request.status().name(),
                request.ackSentAt(),
                request.extendedTo(),
                request.extensionReason(),
                request.completedAt(),
                request.rejectionReason(),
                request.resultRef(),
                request.resultExpiresAt(),
                request.resultDownloadedAt(),
                request.handledBy(),
                request.pseudonymId(),
                request.id());
    }

    @Override
    public Optional<DsarRequest> findByPublicRef(String publicRef) {
        return jdbc.query("""
                SELECT\s""" + COLUMNS + """
                  FROM dsar_request
                 WHERE public_ref = ?
                """, (rs, rowNum) -> readRequest(rs), publicRef).stream().findFirst();
    }

    @Override
    public Optional<DsarRequest> findById(UUID id) {
        return jdbc.query("""
                SELECT\s""" + COLUMNS + """
                  FROM dsar_request
                 WHERE id = ?
                """, (rs, rowNum) -> readRequest(rs), id).stream().findFirst();
    }

    @Override
    public List<DsarRequest> findByUser(UUID userId, Instant before, UUID beforeId, int limit) {
        return jdbc.query("""
                SELECT\s""" + COLUMNS + """
                  FROM dsar_request
                 WHERE user_id = ?
                   AND (received_at < ? OR (received_at = ? AND (?::uuid IS NULL OR id < ?)))
                 ORDER BY received_at DESC, id DESC
                 LIMIT ?
                """, (rs, rowNum) -> readRequest(rs), userId, before, before, beforeId, beforeId, limit);
    }

    @Override
    public Optional<DsarRequest> findLatestExportRequest(UUID userId, Instant since) {
        return jdbc.query("""
                SELECT\s""" + COLUMNS + """
                  FROM dsar_request
                 WHERE user_id = ?
                   AND request_type = 'ACCESS_EXPORT'
                   AND received_at >= ?
                 ORDER BY received_at DESC
                 LIMIT 1
                """, (rs, rowNum) -> readRequest(rs), userId, since).stream().findFirst();
    }

    @Override
    public boolean existsOpenEraseRequest(UUID userId) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*)
                  FROM dsar_request
                 WHERE user_id = ?
                   AND request_type = 'ERASE'
                   AND status NOT IN ('COMPLETED', 'REJECTED')
                """, Integer.class, userId);
        return count != null && count > 0;
    }

    private DsarRequest readRequest(ResultSet rs) {
        try {
            return new DsarRequest(
                    RowReaders.uuid(rs, "id"),
                    RowReaders.requiredString(rs, "public_ref"),
                    RowReaders.uuid(rs, "user_id"),
                    RowReaders.requiredString(rs, "contact_email"),
                    DsarRequestType.valueOf(rs.getString("request_type")),
                    DsarChannel.valueOf(rs.getString("channel")),
                    DsarStatus.valueOf(rs.getString("status")),
                    RowReaders.instant(rs, "identity_verified_at"),
                    RowReaders.string(rs, "identity_method"),
                    RowReaders.requiredInstant(rs, "received_at"),
                    RowReaders.requiredInstant(rs, "ack_due_at"),
                    RowReaders.instant(rs, "ack_sent_at"),
                    RowReaders.requiredInstant(rs, "fulfil_due_at"),
                    RowReaders.instant(rs, "extended_to"),
                    RowReaders.string(rs, "extension_reason"),
                    rs.getBoolean("third_party_involved"),
                    RowReaders.instant(rs, "completed_at"),
                    RowReaders.string(rs, "rejection_reason"),
                    RowReaders.string(rs, "result_ref"),
                    RowReaders.instant(rs, "result_expires_at"),
                    RowReaders.instant(rs, "result_downloaded_at"),
                    RowReaders.uuid(rs, "handled_by"),
                    RowReaders.uuid(rs, "pseudonym_id"),
                    RowReaders.requiredInstant(rs, "created_at"));
        } catch (SQLException ex) {
            throw new IllegalStateException("Đọc dsar_request thất bại", ex);
        }
    }
}
