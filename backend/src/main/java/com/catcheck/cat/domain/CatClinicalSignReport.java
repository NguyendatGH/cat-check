package com.catcheck.cat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "cat_clinical_sign_report")
public class CatClinicalSignReport {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "cat_id", nullable = false)
    private UUID catId;

    @Column(name = "reported_by", nullable = false)
    private UUID reportedBy;

    @Column(name = "signs", nullable = false, columnDefinition = "varchar(32)[]")
    private String[] signs = new String[0];

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 24)
    private ClinicalSignSource source;

    @Column(name = "blocking_shown", nullable = false)
    private boolean blockingShown;

    @Column(name = "reported_at", nullable = false, updatable = false)
    private Instant reportedAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "health_flag_id")
    private UUID healthFlagId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CatClinicalSignReport() {
    }

    public static CatClinicalSignReport create(UUID id, UUID catId, UUID reportedBy,
                                               Set<ClinicalSign> signs, ClinicalSignSource source,
                                               boolean blockingShown, Instant now) {
        Objects.requireNonNull(id, "cat_clinical_sign_report.id phải có giá trị");
        Objects.requireNonNull(catId, "cat_clinical_sign_report.catId phải có giá trị");
        Objects.requireNonNull(reportedBy, "cat_clinical_sign_report.reportedBy phải có giá trị");
        Objects.requireNonNull(source, "cat_clinical_sign_report.source phải có giá trị");
        if (signs == null || signs.isEmpty()) {
            throw new IllegalArgumentException("Phải chọn ít nhất một dấu hiệu lâm sàng");
        }
        CatClinicalSignReport report = new CatClinicalSignReport();
        report.id = id;
        report.catId = catId;
        report.reportedBy = reportedBy;
        report.source = source;
        report.blockingShown = blockingShown;
        report.reportedAt = now;
        report.createdAt = now;
        report.updatedAt = now;
        report.applySigns(signs);
        return report;
    }

    public void acknowledge(Instant now) {
        this.acknowledgedAt = now;
        this.updatedAt = now;
    }

    public void linkHealthFlag(UUID healthFlagId, Instant now) {
        this.healthFlagId = Objects.requireNonNull(healthFlagId, "healthFlagId phải có giá trị");
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCatId() {
        return catId;
    }

    public UUID getReportedBy() {
        return reportedBy;
    }

    public Set<ClinicalSign> clinicalSigns() {
        return Arrays.stream(signs).map(ClinicalSign::valueOf).collect(
                LinkedHashSet::new, Set::add, Set::addAll);
    }

    public ClinicalSignSource getSource() {
        return source;
    }

    public boolean isBlockingShown() {
        return blockingShown;
    }

    public Instant getReportedAt() {
        return reportedAt;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public UUID getHealthFlagId() {
        return healthFlagId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private void applySigns(Set<ClinicalSign> values) {
        this.signs = values.stream().map(Enum::name).toArray(String[]::new);
    }
}
