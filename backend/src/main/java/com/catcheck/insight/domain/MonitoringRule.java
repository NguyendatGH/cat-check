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

@Entity
@Table(name = "monitoring_rule")
public class MonitoringRule {

    @Id
    @Column(name = "code", nullable = false, updatable = false, length = 40)
    private String code;

    @Column(name = "name", nullable = false, columnDefinition = "text")
    private String name;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> params;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    private HealthFlagSeverity severity;

    @Column(name = "cooldown_hours", nullable = false)
    private int cooldownHours;

    @Column(name = "requires_calibrated_chart", nullable = false)
    private boolean requiresCalibratedChart;

    @Column(name = "message_key", nullable = false, length = 80)
    private String messageKey;

    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MonitoringRule() {
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public HealthFlagSeverity getSeverity() {
        return severity;
    }

    public int getCooldownHours() {
        return cooldownHours;
    }

    public boolean isRequiresCalibratedChart() {
        return requiresCalibratedChart;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public boolean isPushEnabled() {
        return pushEnabled;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public double paramAsDouble(String key, double fallback) {
        Object value = params == null ? null : params.get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    public int paramAsInt(String key, int fallback) {
        Object value = params == null ? null : params.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    public boolean paramAsBoolean(String key, boolean fallback) {
        Object value = params == null ? null : params.get(key);
        return value instanceof Boolean bool ? bool : fallback;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void applyAdminUpdate(Boolean newEnabled, Map<String, Object> newParams,
                                 Integer newCooldownHours, Boolean newPushEnabled, Instant now) {
        if (newEnabled != null) {
            this.enabled = newEnabled;
        }
        if (newParams != null) {
            this.params = Map.copyOf(newParams);
        }
        if (newCooldownHours != null) {
            if (newCooldownHours < 0) {
                throw new IllegalArgumentException("cooldownHours phải >= 0 (ck_monitoring_rule_cooldown)");
            }
            if (newCooldownHours == 0 && !MonitoringRuleCode.URGENT_CLINICAL_SIGN.name().equals(code)) {
                throw new IllegalArgumentException(
                        "cooldownHours = 0 chỉ cho URGENT_CLINICAL_SIGN (p4 D11)");
            }
            this.cooldownHours = newCooldownHours;
        }
        if (newPushEnabled != null) {
            this.pushEnabled = newPushEnabled;
        }
        this.updatedAt = now;
    }

    public String paramAsString(String key, String fallback) {
        Object value = params == null ? null : params.get(key);
        return value == null ? fallback : String.valueOf(value);
    }
}
