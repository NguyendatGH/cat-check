package com.catcheck.scan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Nhật ký tính lại (nhẹ) khi backfill bảng màu mới ở chế độ "chỉ ghi nhận" — ánh xạ
 * {@code scan_analysis_recompute} (p4 D4).
 *
 * <p><b>Phạm vi M3 (xem {@code docs/handovers/A6.md}):</b> chỉ tạo bảng theo đúng danh mục
 * migration {@code p4 §4.9.2}. Job backfill thật (L33-L35 của colorchart, so sánh kết quả cũ/mới
 * khi admin publish bảng màu mới) là việc của M7 (hiệu chuẩn bảng màu thật) — ngoài phạm vi MVP
 * theo {@code ORCHESTRATOR.md §2}. Entity này tồn tại để schema đầy đủ và để M7 có chỗ ghi vào,
 * không có application service nào ghi dòng ở M3.</p>
 */
@Entity
@Table(name = "scan_analysis_recompute")
public class ScanAnalysisRecompute {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "scan_analysis_id", nullable = false, updatable = false)
    private UUID scanAnalysisId;

    @Column(name = "chart_id", nullable = false, updatable = false)
    private UUID chartId;

    @Column(name = "chart_version", nullable = false, updatable = false)
    private int chartVersion;

    @Column(name = "ph_value", precision = 3, scale = 1)
    private BigDecimal phValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification", length = 24)
    private ScanClassification classification;

    @Column(name = "confidence", precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(name = "delta_ph", precision = 4, scale = 2)
    private BigDecimal deltaPh;

    @Column(name = "flipped_classification", nullable = false)
    private boolean flippedClassification;

    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "computed_at", nullable = false, updatable = false)
    private Instant computedAt;

    protected ScanAnalysisRecompute() {
    }

    public ScanAnalysisRecompute(UUID id, UUID scanAnalysisId, UUID chartId, int chartVersion,
                                  BigDecimal phValue, ScanClassification classification,
                                  BigDecimal confidence, BigDecimal deltaPh,
                                  boolean flippedClassification, UUID jobId, Instant computedAt) {
        this.id = id;
        this.scanAnalysisId = scanAnalysisId;
        this.chartId = chartId;
        this.chartVersion = chartVersion;
        this.phValue = phValue;
        this.classification = classification;
        this.confidence = confidence;
        this.deltaPh = deltaPh;
        this.flippedClassification = flippedClassification;
        this.jobId = jobId;
        this.computedAt = computedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getScanAnalysisId() {
        return scanAnalysisId;
    }

    public UUID getChartId() {
        return chartId;
    }

    public int getChartVersion() {
        return chartVersion;
    }

    public BigDecimal getPhValue() {
        return phValue;
    }

    public ScanClassification getClassification() {
        return classification;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public BigDecimal getDeltaPh() {
        return deltaPh;
    }

    public boolean isFlippedClassification() {
        return flippedClassification;
    }

    public UUID getJobId() {
        return jobId;
    }

    public Instant getComputedAt() {
        return computedAt;
    }
}
