package com.catcheck.scan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Một lần quét — ánh xạ 1-1 với bảng {@code scan} (p4 D1).
 *
 * <p>Aggregate gốc của module {@code scan}. {@code catId}, {@code userId}, {@code creditLedgerId},
 * {@code chartId}, {@code currentAnalysisId} là các cột UUID thuần trỏ sang bảng của module khác
 * — KHÔNG dùng {@code @ManyToOne} (R6: cấm JPA association trỏ ra ngoài module).</p>
 */
@Entity
@Table(name = "scan")
public class Scan {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "cat_id")
    private UUID catId;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment", nullable = false, length = 20)
    private ScanAssignment assignment;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "capture_source", nullable = false, length = 16)
    private CaptureSource captureSource;

    @Column(name = "device_hint", length = 120)
    private String deviceHint;

    @Column(name = "is_trial", nullable = false)
    private boolean trial;

    @Column(name = "store_image", nullable = false)
    private boolean storeImage;

    @Enumerated(EnumType.STRING)
    @Column(name = "store_image_reason", length = 24)
    private StoreImageReason storeImageReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ScanStatus status;

    @Column(name = "failure_code", length = 48)
    private String failureCode;

    @Column(name = "current_analysis_id")
    private UUID currentAnalysisId;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "credit_ledger_id")
    private UUID creditLedgerId;

    @Column(name = "reassign_count", nullable = false)
    private short reassignCount;

    @Column(name = "disputed_at")
    private Instant disputedAt;

    @Column(name = "disputed_note", columnDefinition = "text")
    private String disputedNote;

    @Column(name = "chart_id")
    private UUID chartId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Scan() {
    }

    public Scan(UUID id, UUID userId, UUID catId, ScanAssignment assignment, Instant capturedAt,
                CaptureSource captureSource, String deviceHint, boolean trial, boolean storeImage,
                StoreImageReason storeImageReason, ScanStatus status, UUID chartId,
                String idempotencyKey, Instant now) {
        this.id = id;
        this.userId = userId;
        this.catId = catId;
        this.assignment = assignment;
        this.capturedAt = capturedAt;
        this.captureSource = captureSource;
        this.deviceHint = deviceHint;
        this.trial = trial;
        this.storeImage = storeImage;
        this.storeImageReason = storeImageReason;
        this.status = status;
        this.chartId = chartId;
        this.idempotencyKey = idempotencyKey;
        this.reassignCount = 0;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getCatId() {
        return catId;
    }

    public ScanAssignment getAssignment() {
        return assignment;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public CaptureSource getCaptureSource() {
        return captureSource;
    }

    public String getDeviceHint() {
        return deviceHint;
    }

    public boolean isTrial() {
        return trial;
    }

    public boolean isStoreImage() {
        return storeImage;
    }

    public StoreImageReason getStoreImageReason() {
        return storeImageReason;
    }

    public ScanStatus getStatus() {
        return status;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public UUID getCurrentAnalysisId() {
        return currentAnalysisId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public UUID getCreditLedgerId() {
        return creditLedgerId;
    }

    public short getReassignCount() {
        return reassignCount;
    }

    public Instant getDisputedAt() {
        return disputedAt;
    }

    public String getDisputedNote() {
        return disputedNote;
    }

    public UUID getChartId() {
        return chartId;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void markAnalyzed(UUID analysisId, UUID creditLedgerId, Instant now) {
        this.status = ScanStatus.ANALYZED;
        this.currentAnalysisId = analysisId;
        this.creditLedgerId = creditLedgerId;
        this.updatedAt = now;
    }

    public void markFailed(String failureCode, Instant now) {
        this.status = ScanStatus.FAILED;
        this.failureCode = failureCode;
        this.updatedAt = now;
    }

    public void reassignTo(UUID newCatId, ScanAssignment newAssignment, Instant now) {
        this.catId = newCatId;
        this.assignment = newAssignment;
        this.reassignCount = (short) (this.reassignCount + 1);
        this.updatedAt = now;
    }

    public void dispute(String note, Instant now) {
        this.disputedAt = now;
        this.disputedNote = note;
        this.updatedAt = now;
    }

    public void clearDispute(Instant now) {
        this.disputedAt = null;
        this.disputedNote = null;
        this.updatedAt = now;
    }

    public void softDelete(Instant now) {
        this.deletedAt = now;
        this.updatedAt = now;
    }

    public boolean isReassignable(Instant now) {
        return deletedAt == null
                && capturedAt.plusSeconds(ScanThresholds.REASSIGN_WINDOW_HOURS * 3600L).isAfter(now)
                && reassignCount < ScanThresholds.REASSIGN_MAX_COUNT;
    }
}
