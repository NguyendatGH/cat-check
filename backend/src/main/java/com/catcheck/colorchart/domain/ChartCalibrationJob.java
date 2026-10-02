package com.catcheck.colorchart.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Job hiệu chuẩn bảng màu — ánh xạ 1-1 với bảng {@code chart_calibration_job} (p4 D10).
 *
 * <p>Luồng 2 (hiệu chuẩn từ ảnh mẫu) là <b>P1</b> — MVP chỉ bắt buộc luồng 1 (nhập tay hex).
 * Entity tồn tại sẵn để schema đúng p4, nhưng job thật chỉ được thêm ở M7.
 */
@Entity
@Table(name = "chart_calibration_job")
public class ChartCalibrationJob {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CalibrationStatus status;

    @Column(name = "source_chart_id")
    private UUID sourceChartId;

    @Column(name = "produced_chart_id")
    private UUID producedChartId;

    @Column(name = "sample_image_count", nullable = false)
    private int sampleImageCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_summary", columnDefinition = "jsonb")
    private Map<String, Object> resultSummary;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ChartCalibrationJob() {
    }

    public ChartCalibrationJob(UUID id, UUID requestedBy, CalibrationStatus status, UUID sourceChartId,
                              UUID producedChartId, int sampleImageCount, Map<String, Object> resultSummary,
                              String failureReason, Instant startedAt, Instant finishedAt,
                              Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.requestedBy = requestedBy;
        this.status = status;
        this.sourceChartId = sourceChartId;
        this.producedChartId = producedChartId;
        this.sampleImageCount = sampleImageCount;
        this.resultSummary = resultSummary;
        this.failureReason = failureReason;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRequestedBy() {
        return requestedBy;
    }

    public CalibrationStatus getStatus() {
        return status;
    }

    public UUID getSourceChartId() {
        return sourceChartId;
    }

    public UUID getProducedChartId() {
        return producedChartId;
    }

    public int getSampleImageCount() {
        return sampleImageCount;
    }

    public Map<String, Object> getResultSummary() {
        return resultSummary;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setStatus(CalibrationStatus status) {
        this.status = status;
    }

    public void setSourceChartId(UUID sourceChartId) {
        this.sourceChartId = sourceChartId;
    }

    public void setProducedChartId(UUID producedChartId) {
        this.producedChartId = producedChartId;
    }

    public void setSampleImageCount(int sampleImageCount) {
        this.sampleImageCount = sampleImageCount;
    }

    public void setResultSummary(Map<String, Object> resultSummary) {
        this.resultSummary = resultSummary;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public void markUpdated(Instant now) {
        this.updatedAt = now;
    }
}
