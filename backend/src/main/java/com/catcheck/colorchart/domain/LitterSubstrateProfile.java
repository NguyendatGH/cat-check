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
 * Màu cát nền theo lô — ánh xạ 1-1 với bảng {@code litter_substrate_profile} (p4 D9).
 *
 * <p>Dùng ở S6 (tách hạt chỉ thị): pixel có {@code C*ab < chromaMax} được coi là nền. Nếu có
 * profile của lô thì dùm làm prior, cảnh báo nếu lệch > 6 ΔE00 so với ước lượng trong ảnh.
 */
@Entity
@Table(name = "litter_substrate_profile")
public class LitterSubstrateProfile {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_line", nullable = false, length = 16)
    private ProductLine productLine;

    @Column(name = "batch_code", length = 64)
    private String batchCode;

    @Column(name = "lab_l", nullable = false, precision = 6, scale = 3)
    private BigDecimal labL;

    @Column(name = "lab_a", nullable = false, precision = 6, scale = 3)
    private BigDecimal labA;

    @Column(name = "lab_b", nullable = false, precision = 6, scale = 3)
    private BigDecimal labB;

    @Column(name = "chroma_max", nullable = false, precision = 5, scale = 2)
    private BigDecimal chromaMax;

    @Column(name = "target_coverage", nullable = false, precision = 5, scale = 4)
    private BigDecimal targetCoverage;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LitterSubstrateProfile() {
    }

    public LitterSubstrateProfile(UUID id, ProductLine productLine, String batchCode,
                                 BigDecimal labL, BigDecimal labA, BigDecimal labB,
                                 BigDecimal chromaMax, BigDecimal targetCoverage, boolean active,
                                 Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.productLine = productLine;
        this.batchCode = batchCode;
        this.labL = labL;
        this.labA = labA;
        this.labB = labB;
        this.chromaMax = chromaMax;
        this.targetCoverage = targetCoverage;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public ProductLine getProductLine() {
        return productLine;
    }

    public String getBatchCode() {
        return batchCode;
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

    public BigDecimal getChromaMax() {
        return chromaMax;
    }

    public BigDecimal getTargetCoverage() {
        return targetCoverage;
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

    public void setBatchCode(String batchCode) {
        this.batchCode = batchCode;
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

    public void setChromaMax(BigDecimal chromaMax) {
        this.chromaMax = chromaMax;
    }

    public void setTargetCoverage(BigDecimal targetCoverage) {
        this.targetCoverage = targetCoverage;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void markUpdated(Instant now) {
        this.updatedAt = now;
    }
}
