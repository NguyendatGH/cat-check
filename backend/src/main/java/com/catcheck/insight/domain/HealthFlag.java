package com.catcheck.insight.domain;

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
 * Một dấu hiệu được rule đánh dấu — ánh xạ {@code health_flag} (p4 D12).
 *
 * <p>{@code catId}, {@code triggerScanId}, {@code acknowledgedBy}, {@code notificationId} là cột
 * UUID thuần trỏ sang bảng của module khác — KHÔNG dùng {@code @ManyToOne} (R6).</p>
 */
@Entity
@Table(name = "health_flag")
public class HealthFlag {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "cat_id", nullable = false, updatable = false)
    private UUID catId;

    @Column(name = "rule_code", nullable = false, updatable = false, length = 40)
    private String ruleCode;

    @Column(name = "trigger_scan_id")
    private UUID triggerScanId;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    private HealthFlagSeverity severity;

    @Column(name = "triggered_at", nullable = false, updatable = false)
    private Instant triggeredAt;

    @Column(name = "window_from", nullable = false, updatable = false)
    private Instant windowFrom;

    @Column(name = "window_to", nullable = false, updatable = false)
    private Instant windowTo;

    @Column(name = "message_key", nullable = false, updatable = false, length = 80)
    private String messageKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "message_params", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> messageParams;

    @Column(name = "explanation_vi", nullable = false, updatable = false, columnDefinition = "text")
    private String explanationVi;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "acknowledged_by")
    private UUID acknowledgedBy;

    @Column(name = "notification_id")
    private UUID notificationId;

    @Column(name = "dedupe_key", nullable = false, updatable = false, length = 120)
    private String dedupeKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HealthFlag() {
    }

    public HealthFlag(UUID id, UUID catId, String ruleCode, UUID triggerScanId, HealthFlagSeverity severity,
                       Instant windowFrom, Instant windowTo, String messageKey, Map<String, Object> messageParams,
                       String explanationVi, String dedupeKey, Instant now) {
        this.id = id;
        this.catId = catId;
        this.ruleCode = ruleCode;
        this.triggerScanId = triggerScanId;
        this.severity = severity;
        this.triggeredAt = now;
        this.windowFrom = windowFrom;
        this.windowTo = windowTo;
        this.messageKey = messageKey;
        this.messageParams = messageParams;
        this.explanationVi = explanationVi;
        this.dedupeKey = dedupeKey;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCatId() {
        return catId;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public UUID getTriggerScanId() {
        return triggerScanId;
    }

    public HealthFlagSeverity getSeverity() {
        return severity;
    }

    public Instant getTriggeredAt() {
        return triggeredAt;
    }

    public Instant getWindowFrom() {
        return windowFrom;
    }

    public Instant getWindowTo() {
        return windowTo;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public Map<String, Object> getMessageParams() {
        return messageParams;
    }

    public String getExplanationVi() {
        return explanationVi;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public UUID getAcknowledgedBy() {
        return acknowledgedBy;
    }

    public UUID getNotificationId() {
        return notificationId;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void acknowledge(UUID userId, Instant now) {
        this.acknowledgedAt = now;
        this.acknowledgedBy = userId;
        this.updatedAt = now;
    }
}
