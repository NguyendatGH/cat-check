package com.catcheck.identity.infrastructure.persistence;

import com.catcheck.identity.domain.MfaResetRequest;
import com.catcheck.identity.domain.MfaResetRequestStatus;
import com.catcheck.identity.domain.port.MfaResetRequestRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link MfaResetRequestRepository} tren {@code JdbcTemplate}.
 *
 * <p>Bang nay KHONG phai append-only (trang thai phai chuyen duoc), nhung
 * {@code reason}, {@code requested_by}, {@code requested_at} khong duoc UPDATE sau khi
 * tao — ep o service, bang chung ben nam o {@code audit_log} (p4 §A9).</p>
 */
@Repository
public class JdbcMfaResetRequestRepository implements MfaResetRequestRepository {

    private static final String COLUMNS = """
            id, target_user_id, requested_by, requested_at, reason, approved_by,
            approved_at, status, reject_reason, expires_at, subject_notified_at,
            created_at, updated_at
            """;

    private final JdbcTemplate jdbc;

    public JdbcMfaResetRequestRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(MfaResetRequest request) {
        jdbc.update("""
                INSERT INTO user_mfa_reset_request (
                    id, target_user_id, requested_by, requested_at, reason, approved_by,
                    approved_at, status, reject_reason, expires_at, subject_notified_at,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                request.id(), request.targetUserId(), request.requestedBy(), utc(request.requestedAt()),
                request.reason(), request.approvedBy(), utc(request.approvedAt()), request.status().name(),
                request.rejectReason(), utc(request.expiresAt()), utc(request.subjectNotifiedAt()),
                utc(request.createdAt()), utc(request.updatedAt()));
    }

    @Override
    public Optional<MfaResetRequest> findById(UUID id) {
        var rows = jdbc.query("SELECT " + COLUMNS + " FROM user_mfa_reset_request WHERE id = ?",
                (rs, rowNum) -> map(rs), id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public Optional<MfaResetRequest> findPendingByTargetUserId(UUID targetUserId) {
        var rows = jdbc.query("SELECT " + COLUMNS + " FROM user_mfa_reset_request"
                        + " WHERE target_user_id = ? AND status = ?",
                (rs, rowNum) -> map(rs), targetUserId, MfaResetRequestStatus.PENDING.name());
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public List<MfaResetRequest> findPendingQueue() {
        return jdbc.query("SELECT " + COLUMNS + " FROM user_mfa_reset_request"
                        + " WHERE status = ? ORDER BY requested_at",
                (rs, rowNum) -> map(rs), MfaResetRequestStatus.PENDING.name());
    }

    @Override
    public void markApproved(UUID id, UUID approvedBy, Instant approvedAt) {
        jdbc.update("""
                UPDATE user_mfa_reset_request
                   SET status = ?, approved_by = ?, approved_at = ?
                 WHERE id = ?
                """, MfaResetRequestStatus.APPROVED.name(), approvedBy, utc(approvedAt), id);
    }

    @Override
    public void markRejected(UUID id, UUID approvedBy, Instant approvedAt, String rejectReason) {
        jdbc.update("""
                UPDATE user_mfa_reset_request
                   SET status = ?, approved_by = ?, approved_at = ?, reject_reason = ?
                 WHERE id = ?
                """, MfaResetRequestStatus.REJECTED.name(), approvedBy, utc(approvedAt), rejectReason, id);
    }

    @Override
    public void markCancelled(UUID id) {
        jdbc.update("UPDATE user_mfa_reset_request SET status = ? WHERE id = ?",
                MfaResetRequestStatus.CANCELLED.name(), id);
    }

    @Override
    public void markExpired(UUID id) {
        jdbc.update("UPDATE user_mfa_reset_request SET status = ? WHERE id = ?",
                MfaResetRequestStatus.EXPIRED.name(), id);
    }

    @Override
    public void markSubjectNotified(UUID id, Instant notifiedAt) {
        jdbc.update("UPDATE user_mfa_reset_request SET subject_notified_at = ? WHERE id = ?",
                utc(notifiedAt), id);
    }

    @Override
    public int countRequestedBySince(UUID requestedBy, Instant since) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_mfa_reset_request WHERE requested_by = ? AND requested_at >= ?",
                Integer.class, requestedBy, utc(since));
        return count == null ? 0 : count;
    }

    private MfaResetRequest map(ResultSet rs) throws SQLException {
        return new MfaResetRequest(
                RowReaders.uuid(rs, "id"),
                RowReaders.uuid(rs, "target_user_id"),
                RowReaders.uuid(rs, "requested_by"),
                RowReaders.requiredInstant(rs, "requested_at"),
                RowReaders.text(rs, "reason"),
                RowReaders.uuidOrNull(rs, "approved_by"),
                RowReaders.instant(rs, "approved_at"),
                RowReaders.requiredEnum(rs, "status", MfaResetRequestStatus.class),
                RowReaders.textOrNull(rs, "reject_reason"),
                RowReaders.requiredInstant(rs, "expires_at"),
                RowReaders.instant(rs, "subject_notified_at"),
                RowReaders.requiredInstant(rs, "created_at"),
                RowReaders.requiredInstant(rs, "updated_at"));
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
