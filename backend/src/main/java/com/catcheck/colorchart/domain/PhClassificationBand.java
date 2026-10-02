package com.catcheck.colorchart.domain;

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
 * Dải phân loại pH hiển thị — ánh xạ 1-1 với bảng {@code ph_classification_band} (p4 D7).
 *
 * <p>{@code chartId = null} nghĩa là dải <b>toàn cục</b> (áp cho mọi bảng màu). Khác null = ghi đè
 * riêng cho một bảng màu.
 *
 * <p>{@code colorToken} lưu <b>tên token</b> thiết kế ({@code color-ph-*}), không lưu mã hex —
 * đổi theme thì không phải UPDATE dữ liệu (p4 D7).
 */
@Entity
@Table(name = "ph_classification_band")
public class PhClassificationBand {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "chart_id", updatable = false)
    private UUID chartId;

    @Column(name = "code", nullable = false, length = 24)
    private String code;

    @Column(name = "min_ph", precision = 3, scale = 1)
    private BigDecimal minPh;

    @Column(name = "max_ph", precision = 3, scale = 1)
    private BigDecimal maxPh;

    @Column(name = "min_inclusive", nullable = false)
    private boolean minInclusive;

    @Column(name = "max_inclusive", nullable = false)
    private boolean maxInclusive;

    @Column(name = "label_vi", nullable = false, columnDefinition = "text")
    private String labelVi;

    @Column(name = "label_en", columnDefinition = "text")
    private String labelEn;

    @Column(name = "description_vi", columnDefinition = "text")
    private String descriptionVi;

    @Column(name = "description_en", columnDefinition = "text")
    private String descriptionEn;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    private BandSeverity severity;

    @Column(name = "color_token", nullable = false, length = 48)
    private String colorToken;

    @Column(name = "icon_name", nullable = false, length = 48)
    private String iconName;

    @Column(name = "triggers_alert", nullable = false)
    private boolean triggersAlert;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PhClassificationBand() {
    }

    public PhClassificationBand(UUID id, UUID chartId, String code, BigDecimal minPh, BigDecimal maxPh,
                                boolean minInclusive, boolean maxInclusive, String labelVi, String labelEn,
                                String descriptionVi, String descriptionEn, BandSeverity severity,
                                String colorToken, String iconName, boolean triggersAlert, int sortOrder,
                                boolean active, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.chartId = chartId;
        this.code = code;
        this.minPh = minPh;
        this.maxPh = maxPh;
        this.minInclusive = minInclusive;
        this.maxInclusive = maxInclusive;
        this.labelVi = labelVi;
        this.labelEn = labelEn;
        this.descriptionVi = descriptionVi;
        this.descriptionEn = descriptionEn;
        this.severity = severity;
        this.colorToken = colorToken;
        this.iconName = iconName;
        this.triggersAlert = triggersAlert;
        this.sortOrder = sortOrder;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getChartId() {
        return chartId;
    }

    public String getCode() {
        return code;
    }

    public BigDecimal getMinPh() {
        return minPh;
    }

    public BigDecimal getMaxPh() {
        return maxPh;
    }

    public boolean isMinInclusive() {
        return minInclusive;
    }

    public boolean isMaxInclusive() {
        return maxInclusive;
    }

    public String getLabelVi() {
        return labelVi;
    }

    public String getLabelEn() {
        return labelEn;
    }

    public String getDescriptionVi() {
        return descriptionVi;
    }

    public String getDescriptionEn() {
        return descriptionEn;
    }

    public BandSeverity getSeverity() {
        return severity;
    }

    public String getColorToken() {
        return colorToken;
    }

    public String getIconName() {
        return iconName;
    }

    public boolean isTriggersAlert() {
        return triggersAlert;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setMinPh(BigDecimal minPh) {
        this.minPh = minPh;
    }

    public void setMaxPh(BigDecimal maxPh) {
        this.maxPh = maxPh;
    }

    public void setMinInclusive(boolean minInclusive) {
        this.minInclusive = minInclusive;
    }

    public void setMaxInclusive(boolean maxInclusive) {
        this.maxInclusive = maxInclusive;
    }

    public void setLabelVi(String labelVi) {
        this.labelVi = labelVi;
    }

    public void setLabelEn(String labelEn) {
        this.labelEn = labelEn;
    }

    public void setDescriptionVi(String descriptionVi) {
        this.descriptionVi = descriptionVi;
    }

    public void setDescriptionEn(String descriptionEn) {
        this.descriptionEn = descriptionEn;
    }

    public void setSeverity(BandSeverity severity) {
        this.severity = severity;
    }

    public void setColorToken(String colorToken) {
        this.colorToken = colorToken;
    }

    public void setIconName(String iconName) {
        this.iconName = iconName;
    }

    public void setTriggersAlert(boolean triggersAlert) {
        this.triggersAlert = triggersAlert;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void markUpdated(Instant now) {
        this.updatedAt = now;
    }
}
