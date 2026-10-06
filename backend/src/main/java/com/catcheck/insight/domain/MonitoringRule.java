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

/**
 * Cấu hình một rule cảnh báo — ánh xạ {@code monitoring_rule} (p4 D11).
 *
 * <p>Bảng cấu hình nhỏ (5 dòng seed), nạp toàn bộ vào cache ứng dụng — xem
 * {@code MonitoringRuleCache}.</p>
 */
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

    /** Mốc sửa gần nhất — nguồn của {@code ETag}/{@code If-Match} ở L38/L39 (p8 §8.1.11). */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Áp thay đổi của L39: bật/tắt, tham số, cooldown (p8 §8.4.12 L39).
     *
     * <p><b>Chỉ ba trường đó.</b> {@code severity} và {@code message_key} cố ý KHÔNG sửa được
     * qua API: p6 §6.9.7 ràng buộc {@code URGENT} chỉ thuộc {@code URGENT_CLINICAL_SIGN}, và
     * {@code message_key} là khoá i18n phải tồn tại trong bundle — cho admin nhập tự do hai
     * trường đó là mở đường cho một rule im lặng không hiển thị được gì.
     *
     * <p>Mỗi tham số {@code null} = "không đổi" (merge-patch, p8 §8.1.11). Entity này vốn
     * chỉ-đọc (hàng cấu hình do Flyway seed); đây là đường ghi DUY NHẤT, nên luật nằm ở đây
     * chứ không rải ra setter rời.
     */
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
                // p4 D11: "0 = không cooldown, CHỈ dùng cho URGENT_CLINICAL_SIGN". Để rule khác
                // về 0 nghĩa là mỗi lần quét đều bắn một flag — spam, và người dùng tắt thông
                // báo thì mất luôn cảnh báo thật.
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
