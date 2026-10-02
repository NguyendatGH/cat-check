package com.catcheck.colorchart.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Một mức pH trên bảng màu — ánh xạ 1-1 với bảng {@code color_chart_point} (p4 D6).
 *
 * <p>{@code labL/labA/labB} là <b>nguồn sự thật cho phép khớp</b> (p6 S8) — không phải
 * {@code hexSrgb}. {@code displayHex} có thể khác {@code hexSrgb} vì UI cần màu dễ nhìn.
 */
@Entity
@Table(name = "color_chart_point")
public class ColorChartPoint {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "chart_id", nullable = false, updatable = false)
    private UUID chartId;

    @Column(name = "ph_value", nullable = false, precision = 3, scale = 1)
    private BigDecimal phValue;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "lab_l", nullable = false, precision = 6, scale = 3)
    private BigDecimal labL;

    @Column(name = "lab_a", nullable = false, precision = 6, scale = 3)
    private BigDecimal labA;

    @Column(name = "lab_b", nullable = false, precision = 6, scale = 3)
    private BigDecimal labB;

    @Column(name = "tolerance_delta_e", nullable = false, precision = 5, scale = 2)
    private BigDecimal toleranceDeltaE;

    @Column(name = "hex_srgb", length = 7)
    private String hexSrgb;

    @Column(name = "display_hex", nullable = false, length = 7)
    private String displayHex;

    @Column(name = "display_name_vi", nullable = false, columnDefinition = "text")
    private String displayNameVi;

    @Column(name = "display_name_en", columnDefinition = "text")
    private String displayNameEn;

    @Column(name = "sample_count")
    private Integer sampleCount;

    @Column(name = "sample_spread_de00", precision = 5, scale = 2)
    private BigDecimal sampleSpreadDe00;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ColorChartPoint() {
    }

    public ColorChartPoint(UUID id, UUID chartId, BigDecimal phValue, int sortOrder,
                           BigDecimal labL, BigDecimal labA, BigDecimal labB,
                           BigDecimal toleranceDeltaE, String hexSrgb, String displayHex,
                           String displayNameVi, String displayNameEn, Integer sampleCount,
                           BigDecimal sampleSpreadDe00, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.chartId = chartId;
        this.phValue = phValue;
        this.sortOrder = sortOrder;
        this.labL = labL;
        this.labA = labA;
        this.labB = labB;
        this.toleranceDeltaE = toleranceDeltaE;
        this.hexSrgb = hexSrgb;
        this.displayHex = displayHex;
        this.displayNameVi = displayNameVi;
        this.displayNameEn = displayNameEn;
        this.sampleCount = sampleCount;
        this.sampleSpreadDe00 = sampleSpreadDe00;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getChartId() {
        return chartId;
    }

    public BigDecimal getPhValue() {
        return phValue;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public BigDecimal getLabL() {
        return labL;
    }

    public BigDecimal getLabA() {
        return labA;
    }

    public BigDecimal getLabB() {
        return labB;
    }

    public BigDecimal getToleranceDeltaE() {
        return toleranceDeltaE;
    }

    public String getHexSrgb() {
        return hexSrgb;
    }

    public String getDisplayHex() {
        return displayHex;
    }

    public String getDisplayNameVi() {
        return displayNameVi;
    }

    public String getDisplayNameEn() {
        return displayNameEn;
    }

    public Integer getSampleCount() {
        return sampleCount;
    }

    public BigDecimal getSampleSpreadDe00() {
        return sampleSpreadDe00;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setPhValue(BigDecimal phValue) {
        this.phValue = phValue;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void setLabL(BigDecimal labL) {
        this.labL = labL;
    }

    public void setLabA(BigDecimal labA) {
        this.labA = labA;
    }

    public void setLabB(BigDecimal labB) {
        this.labB = labB;
    }

    public void setToleranceDeltaE(BigDecimal toleranceDeltaE) {
        this.toleranceDeltaE = toleranceDeltaE;
    }

    public void setHexSrgb(String hexSrgb) {
        this.hexSrgb = hexSrgb;
    }

    public void setDisplayHex(String displayHex) {
        this.displayHex = displayHex;
    }

    public void setDisplayNameVi(String displayNameVi) {
        this.displayNameVi = displayNameVi;
    }

    public void setDisplayNameEn(String displayNameEn) {
        this.displayNameEn = displayNameEn;
    }

    public void setSampleCount(Integer sampleCount) {
        this.sampleCount = sampleCount;
    }

    public void setSampleSpreadDe00(BigDecimal sampleSpreadDe00) {
        this.sampleSpreadDe00 = sampleSpreadDe00;
    }

    public void markUpdated(Instant now) {
        this.updatedAt = now;
    }
}
