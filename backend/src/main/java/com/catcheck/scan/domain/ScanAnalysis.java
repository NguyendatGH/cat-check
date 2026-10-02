package com.catcheck.scan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kết quả phân tích của một lần quét — ánh xạ {@code scan_analysis} (p4 D3).
 *
 * <p>Bảng mang giá trị kỹ thuật lớn nhất của module: cho phép debug không cần ảnh, tính lại khi
 * đổi bảng màu (backfill, M7 - ngoài phạm vi), và đo chất lượng pipeline theo thời gian. Mỗi lần
 * quét có thể có nhiều dòng (một dòng gốc + các lần tính lại) nhưng chỉ một dòng
 * {@code is_current = true} tại một thời điểm (partial unique index).</p>
 */
@Entity
@Table(name = "scan_analysis")
public class ScanAnalysis {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "scan_id", nullable = false, updatable = false)
    private UUID scanId;

    @Column(name = "is_current", nullable = false)
    private boolean current;

    @Column(name = "ph_value", precision = 3, scale = 1)
    private BigDecimal phValue;

    @Column(name = "ph_low", precision = 3, scale = 1)
    private BigDecimal phLow;

    @Column(name = "ph_high", precision = 3, scale = 1)
    private BigDecimal phHigh;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification", nullable = false, length = 24)
    private ScanClassification classification;

    @Column(name = "band_id")
    private UUID bandId;

    @Column(name = "confidence", nullable = false, precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(name = "near_boundary", nullable = false)
    private boolean nearBoundary;

    @Column(name = "lab_l", precision = 6, scale = 3)
    private BigDecimal labL;

    @Column(name = "lab_a", precision = 6, scale = 3)
    private BigDecimal labA;

    @Column(name = "lab_b", precision = 6, scale = 3)
    private BigDecimal labB;

    @Column(name = "lab_spread_de00", precision = 5, scale = 2)
    private BigDecimal labSpreadDe00;

    @Column(name = "blob_count")
    private Integer blobCount;

    @Column(name = "indicator_pixel_ratio", precision = 5, scale = 4)
    private BigDecimal indicatorPixelRatio;

    @Column(name = "substrate_lab_l", precision = 6, scale = 3)
    private BigDecimal substrateLabL;

    @Column(name = "substrate_lab_a", precision = 6, scale = 3)
    private BigDecimal substrateLabA;

    @Column(name = "substrate_lab_b", precision = 6, scale = 3)
    private BigDecimal substrateLabB;

    @Column(name = "delta_e_min", precision = 6, scale = 3)
    private BigDecimal deltaEMin;

    @Column(name = "perp_residual_de00", precision = 6, scale = 3)
    private BigDecimal perpResidualDe00;

    @Column(name = "match_percent")
    private Integer matchPercent;

    @Column(name = "matched_point_id")
    private UUID matchedPointId;

    @Column(name = "matched_segment_k")
    private Integer matchedSegmentK;

    @Column(name = "matched_t", precision = 5, scale = 4)
    private BigDecimal matchedT;

    @Enumerated(EnumType.STRING)
    @Column(name = "calibration_method", nullable = false, length = 20)
    private CalibrationMethod calibrationMethod;

    @Column(name = "calibration_residual_de00", precision = 6, scale = 3)
    private BigDecimal calibrationResidualDe00;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "calibration", columnDefinition = "jsonb")
    private Map<String, Object> calibration;

    @Column(name = "card_layout_id")
    private UUID cardLayoutId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quality_metrics", columnDefinition = "jsonb")
    private Map<String, Object> qualityMetrics;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quality_flags", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> qualityFlags;

    @Column(name = "chart_id")
    private UUID chartId;

    @Column(name = "chart_version")
    private Integer chartVersion;

    @Column(name = "engine_version", nullable = false, length = 24)
    private String engineVersion;

    @Column(name = "processing_ms")
    private Integer processingMs;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    @Column(name = "recompute_of")
    private UUID recomputeOf;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ScanAnalysis() {
    }

    public ScanAnalysis(UUID id, UUID scanId, Instant now) {
        this.id = id;
        this.scanId = scanId;
        this.current = true;
        this.qualityFlags = List.of();
        this.computedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getScanId() {
        return scanId;
    }

    public boolean isCurrent() {
        return current;
    }

    public void setCurrent(boolean current, Instant now) {
        this.current = current;
        this.updatedAt = now;
    }

    public BigDecimal getPhValue() {
        return phValue;
    }

    public void setPhValue(BigDecimal phValue) {
        this.phValue = phValue;
    }

    public BigDecimal getPhLow() {
        return phLow;
    }

    public void setPhLow(BigDecimal phLow) {
        this.phLow = phLow;
    }

    public BigDecimal getPhHigh() {
        return phHigh;
    }

    public void setPhHigh(BigDecimal phHigh) {
        this.phHigh = phHigh;
    }

    public ScanClassification getClassification() {
        return classification;
    }

    public void setClassification(ScanClassification classification) {
        this.classification = classification;
    }

    public UUID getBandId() {
        return bandId;
    }

    public void setBandId(UUID bandId) {
        this.bandId = bandId;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public boolean isNearBoundary() {
        return nearBoundary;
    }

    public void setNearBoundary(boolean nearBoundary) {
        this.nearBoundary = nearBoundary;
    }

    public BigDecimal getLabL() {
        return labL;
    }

    public void setLabL(BigDecimal labL) {
        this.labL = labL;
    }

    public BigDecimal getLabA() {
        return labA;
    }

    public void setLabA(BigDecimal labA) {
        this.labA = labA;
    }

    public BigDecimal getLabB() {
        return labB;
    }

    public void setLabB(BigDecimal labB) {
        this.labB = labB;
    }

    public BigDecimal getLabSpreadDe00() {
        return labSpreadDe00;
    }

    public void setLabSpreadDe00(BigDecimal labSpreadDe00) {
        this.labSpreadDe00 = labSpreadDe00;
    }

    public Integer getBlobCount() {
        return blobCount;
    }

    public void setBlobCount(Integer blobCount) {
        this.blobCount = blobCount;
    }

    public BigDecimal getIndicatorPixelRatio() {
        return indicatorPixelRatio;
    }

    public void setIndicatorPixelRatio(BigDecimal indicatorPixelRatio) {
        this.indicatorPixelRatio = indicatorPixelRatio;
    }

    public BigDecimal getSubstrateLabL() {
        return substrateLabL;
    }

    public void setSubstrateLabL(BigDecimal substrateLabL) {
        this.substrateLabL = substrateLabL;
    }

    public BigDecimal getSubstrateLabA() {
        return substrateLabA;
    }

    public void setSubstrateLabA(BigDecimal substrateLabA) {
        this.substrateLabA = substrateLabA;
    }

    public BigDecimal getSubstrateLabB() {
        return substrateLabB;
    }

    public void setSubstrateLabB(BigDecimal substrateLabB) {
        this.substrateLabB = substrateLabB;
    }

    public BigDecimal getDeltaEMin() {
        return deltaEMin;
    }

    public void setDeltaEMin(BigDecimal deltaEMin) {
        this.deltaEMin = deltaEMin;
    }

    public BigDecimal getPerpResidualDe00() {
        return perpResidualDe00;
    }

    public void setPerpResidualDe00(BigDecimal perpResidualDe00) {
        this.perpResidualDe00 = perpResidualDe00;
    }

    public Integer getMatchPercent() {
        return matchPercent;
    }

    public void setMatchPercent(Integer matchPercent) {
        this.matchPercent = matchPercent;
    }

    public UUID getMatchedPointId() {
        return matchedPointId;
    }

    public void setMatchedPointId(UUID matchedPointId) {
        this.matchedPointId = matchedPointId;
    }

    public Integer getMatchedSegmentK() {
        return matchedSegmentK;
    }

    public void setMatchedSegmentK(Integer matchedSegmentK) {
        this.matchedSegmentK = matchedSegmentK;
    }

    public BigDecimal getMatchedT() {
        return matchedT;
    }

    public void setMatchedT(BigDecimal matchedT) {
        this.matchedT = matchedT;
    }

    public CalibrationMethod getCalibrationMethod() {
        return calibrationMethod;
    }

    public void setCalibrationMethod(CalibrationMethod calibrationMethod) {
        this.calibrationMethod = calibrationMethod;
    }

    public BigDecimal getCalibrationResidualDe00() {
        return calibrationResidualDe00;
    }

    public void setCalibrationResidualDe00(BigDecimal calibrationResidualDe00) {
        this.calibrationResidualDe00 = calibrationResidualDe00;
    }

    public Map<String, Object> getCalibration() {
        return calibration;
    }

    public void setCalibration(Map<String, Object> calibration) {
        this.calibration = calibration;
    }

    public UUID getCardLayoutId() {
        return cardLayoutId;
    }

    public void setCardLayoutId(UUID cardLayoutId) {
        this.cardLayoutId = cardLayoutId;
    }

    public Map<String, Object> getQualityMetrics() {
        return qualityMetrics;
    }

    public void setQualityMetrics(Map<String, Object> qualityMetrics) {
        this.qualityMetrics = qualityMetrics;
    }

    public List<Map<String, Object>> getQualityFlags() {
        return qualityFlags;
    }

    public void setQualityFlags(List<Map<String, Object>> qualityFlags) {
        this.qualityFlags = qualityFlags == null ? List.of() : qualityFlags;
    }

    public UUID getChartId() {
        return chartId;
    }

    public void setChartId(UUID chartId) {
        this.chartId = chartId;
    }

    public Integer getChartVersion() {
        return chartVersion;
    }

    public void setChartVersion(Integer chartVersion) {
        this.chartVersion = chartVersion;
    }

    public String getEngineVersion() {
        return engineVersion;
    }

    public void setEngineVersion(String engineVersion) {
        this.engineVersion = engineVersion;
    }

    public Integer getProcessingMs() {
        return processingMs;
    }

    public void setProcessingMs(Integer processingMs) {
        this.processingMs = processingMs;
    }

    public Instant getComputedAt() {
        return computedAt;
    }

    public UUID getRecomputeOf() {
        return recomputeOf;
    }

    public void setRecomputeOf(UUID recomputeOf) {
        this.recomputeOf = recomputeOf;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
