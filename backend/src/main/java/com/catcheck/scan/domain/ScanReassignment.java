package com.catcheck.scan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Nhật ký đổi mèo của một lần quét — ánh xạ {@code scan_reassignment} (p4 D13).
 *
 * <p>Bảng append-only (p4 §4.1.2): KHÔNG có {@code updated_at}, không trigger — cùng nhóm với
 * {@code credit_ledger}/{@code audit_log}. {@code scan.reassign_count} chỉ là bản đếm nhanh; số
 * dòng ở đây là nguồn sự thật (bất biến I12).</p>
 */
@Entity
@Table(name = "scan_reassignment")
public class ScanReassignment {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "scan_id", nullable = false, updatable = false)
    private UUID scanId;

    @Column(name = "from_cat_id", updatable = false)
    private UUID fromCatId;

    @Column(name = "to_cat_id", updatable = false)
    private UUID toCatId;

    @Column(name = "from_assignment", nullable = false, updatable = false, length = 20)
    private String fromAssignment;

    @Column(name = "to_assignment", nullable = false, updatable = false, length = 20)
    private String toAssignment;

    @Column(name = "changed_by", nullable = false, updatable = false)
    private UUID changedBy;

    @Column(name = "changed_by_role", nullable = false, updatable = false, length = 32)
    private String changedByRole;

    @Column(name = "reason", updatable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    protected ScanReassignment() {
    }

    public ScanReassignment(UUID id, UUID scanId, UUID fromCatId, UUID toCatId,
                             ScanAssignment fromAssignment, ScanAssignment toAssignment,
                             UUID changedBy, String changedByRole, String reason, Instant changedAt) {
        this.id = id;
        this.scanId = scanId;
        this.fromCatId = fromCatId;
        this.toCatId = toCatId;
        this.fromAssignment = fromAssignment.name();
        this.toAssignment = toAssignment.name();
        this.changedBy = changedBy;
        this.changedByRole = changedByRole;
        this.reason = reason;
        this.changedAt = changedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getScanId() {
        return scanId;
    }

    public UUID getFromCatId() {
        return fromCatId;
    }

    public UUID getToCatId() {
        return toCatId;
    }

    public String getFromAssignment() {
        return fromAssignment;
    }

    public String getToAssignment() {
        return toAssignment;
    }

    public UUID getChangedBy() {
        return changedBy;
    }

    public String getChangedByRole() {
        return changedByRole;
    }

    public String getReason() {
        return reason;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
