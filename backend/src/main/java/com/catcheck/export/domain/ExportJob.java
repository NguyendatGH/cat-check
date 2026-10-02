package com.catcheck.export.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Yêu cầu xuất hồ sơ PDF — ánh xạ {@code export_job} (p4 G1).
 *
 * <p>{@code userId}, {@code catId} là cột UUID thuần trỏ sang module khác — KHÔNG dùng
 * {@code @ManyToOne} (R6).</p>
 */
@Entity
@Table(name = "export_job")
public class ExportJob {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "cat_id", nullable = false, updatable = false)
    private UUID catId;

    @Column(name = "format", nullable = false, updatable = false, length = 8)
    private String format;

    @Column(name = "document_code", nullable = false, updatable = false, length = 24)
    private String documentCode;

    @Column(name = "range_from", nullable = false, updatable = false)
    private LocalDate rangeFrom;

    @Column(name = "range_to", nullable = false, updatable = false)
    private LocalDate rangeTo;

    @Column(name = "range_preset", updatable = false, length = 16)
    private String rangePreset;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sections", nullable = false, columnDefinition = "jsonb")
    private List<String> sections;

    @Column(name = "locale", nullable = false, updatable = false, length = 8)
    private String locale;

    @Column(name = "timezone", nullable = false, updatable = false, length = 64)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private ExportStatus status;

    @Column(name = "storage_provider", length = 16)
    private String storageProvider;

    @Column(name = "file_ref", columnDefinition = "text")
    private String fileRef;

    @Column(name = "file_bytes")
    private Long fileBytes;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "scan_count")
    private Integer scanCount;

    @Column(name = "chart_version_snapshot")
    private Integer chartVersionSnapshot;

    @Column(name = "download_count", nullable = false)
    private int downloadCount;

    @Column(name = "last_downloaded_at")
    private Instant lastDownloadedAt;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected ExportJob() {
    }

    public ExportJob(UUID id, UUID userId, UUID catId, String documentCode, LocalDate rangeFrom, LocalDate rangeTo,
                      String rangePreset, List<String> sections, String locale, String timezone, Instant now) {
        this.id = id;
        this.userId = userId;
        this.catId = catId;
        this.format = "PDF";
        this.documentCode = documentCode;
        this.rangeFrom = rangeFrom;
        this.rangeTo = rangeTo;
        this.rangePreset = rangePreset;
        this.sections = sections;
        this.locale = locale;
        this.timezone = timezone;
        this.status = ExportStatus.QUEUED;
        this.downloadCount = 0;
        this.requestedAt = now;
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

    public String getFormat() {
        return format;
    }

    public String getDocumentCode() {
        return documentCode;
    }

    public LocalDate getRangeFrom() {
        return rangeFrom;
    }

    public LocalDate getRangeTo() {
        return rangeTo;
    }

    public String getRangePreset() {
        return rangePreset;
    }

    public List<String> getSections() {
        return sections;
    }

    public String getLocale() {
        return locale;
    }

    public String getTimezone() {
        return timezone;
    }

    public ExportStatus getStatus() {
        return status;
    }

    public String getStorageProvider() {
        return storageProvider;
    }

    public String getFileRef() {
        return fileRef;
    }

    public Long getFileBytes() {
        return fileBytes;
    }

    public Integer getPageCount() {
        return pageCount;
    }

    public Integer getScanCount() {
        return scanCount;
    }

    public Integer getChartVersionSnapshot() {
        return chartVersionSnapshot;
    }

    public int getDownloadCount() {
        return downloadCount;
    }

    public Instant getLastDownloadedAt() {
        return lastDownloadedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void markRunning() {
        this.status = ExportStatus.RUNNING;
    }

    public void markReady(String storageProvider, String fileRef, long fileBytes, int pageCount, int scanCount,
                           Integer chartVersionSnapshot, Instant now) {
        this.status = ExportStatus.READY;
        this.storageProvider = storageProvider;
        this.fileRef = fileRef;
        this.fileBytes = fileBytes;
        this.pageCount = pageCount;
        this.scanCount = scanCount;
        this.chartVersionSnapshot = chartVersionSnapshot;
        this.completedAt = now;
        this.expiresAt = now.plusSeconds(7L * 24 * 3600);
    }

    public void markFailed(String reason, Instant now) {
        this.status = ExportStatus.FAILED;
        this.failureReason = reason;
        this.completedAt = now;
    }

    public void markExpired() {
        this.status = ExportStatus.EXPIRED;
        this.fileRef = null;
    }

    public void recordDownload(Instant now) {
        this.downloadCount = this.downloadCount + 1;
        this.lastDownloadedAt = now;
    }

    public boolean isActive() {
        return status == ExportStatus.QUEUED || status == ExportStatus.RUNNING;
    }
}
