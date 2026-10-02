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
 * Bảng màu pH — ánh xạ 1-1 với bảng {@code color_chart} (p4 D5).
 *
 * <p>Đây là aggregate gốc của module {@code colorchart}. {@code cardLayoutId} và {@code createdBy}
 * là cột UUID thuần trỏ sang {@code reference_card_layout} / {@code app_user} — KHÔNG dùng
 * {@code @ManyToOne} (R6: cấm association JPA trỏ ra ngoài module).
 *
 * <p>Không có cột {@code version} riêng cho {@code If-Match}: ETag lấy từ {@code updatedAt}
 * (p8 §8.1.11).
 */
@Entity
@Table(name = "color_chart")
public class ColorChart {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "name", nullable = false, columnDefinition = "text")
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_line", length = 16)
    private ProductLine productLine;

    @Column(name = "production_batch", length = 64)
    private String productionBatch;

    @Column(name = "card_layout_id")
    private UUID cardLayoutId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ChartStatus status;

    @Column(name = "is_placeholder", nullable = false)
    private boolean placeholder;

    @Column(name = "illuminant", nullable = false, length = 8)
    private String illuminant;

    @Column(name = "observer", nullable = false, length = 8)
    private String observer;

    @Column(name = "delta_e_formula", nullable = false, length = 16)
    private String deltaEFormula;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> params;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private ChartSource source;

    @Column(name = "calibration_job_id")
    private UUID calibrationJobId;

    @Column(name = "effective_from")
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ColorChart() {
    }

    public ColorChart(UUID id, String code, int version, String name, ProductLine productLine,
                      String productionBatch, UUID cardLayoutId, ChartStatus status, boolean placeholder,
                      String illuminant, String observer, String deltaEFormula,
                      Map<String, Object> params, ChartSource source, UUID calibrationJobId,
                      Instant effectiveFrom, Instant effectiveTo, Instant publishedAt, UUID createdBy,
                      Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.code = code;
        this.version = version;
        this.name = name;
        this.productLine = productLine;
        this.productionBatch = productionBatch;
        this.cardLayoutId = cardLayoutId;
        this.status = status;
        this.placeholder = placeholder;
        this.illuminant = illuminant;
        this.observer = observer;
        this.deltaEFormula = deltaEFormula;
        this.params = params;
        this.source = source;
        this.calibrationJobId = calibrationJobId;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.publishedAt = publishedAt;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public int getVersion() {
        return version;
    }

    public String getName() {
        return name;
    }

    public ProductLine getProductLine() {
        return productLine;
    }

    public String getProductionBatch() {
        return productionBatch;
    }

    public UUID getCardLayoutId() {
        return cardLayoutId;
    }

    public ChartStatus getStatus() {
        return status;
    }

    public boolean isPlaceholder() {
        return placeholder;
    }

    public String getIlluminant() {
        return illuminant;
    }

    public String getObserver() {
        return observer;
    }

    public String getDeltaEFormula() {
        return deltaEFormula;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public ChartSource getSource() {
        return source;
    }

    public UUID getCalibrationJobId() {
        return calibrationJobId;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public Instant getEffectiveTo() {
        return effectiveTo;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setProductLine(ProductLine productLine) {
        this.productLine = productLine;
    }

    public void setProductionBatch(String productionBatch) {
        this.productionBatch = productionBatch;
    }

    public void setCardLayoutId(UUID cardLayoutId) {
        this.cardLayoutId = cardLayoutId;
    }

    public void setPlaceholder(boolean placeholder) {
        this.placeholder = placeholder;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public void setEffectiveFrom(Instant effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public void setEffectiveTo(Instant effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public void setStatus(ChartStatus status) {
        this.status = status;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public void markUpdated(Instant now) {
        this.updatedAt = now;
    }
}
