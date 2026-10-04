package com.catcheck.insight.domain;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;

/**
 * Dựng {@link MonitoringRule} cho test.
 *
 * <p>{@code MonitoringRule} là entity JPA chỉ-đọc (hàng cấu hình do Flyway seed, p4 D11): không
 * có constructor công khai và không có setter — đúng như nó phải thế. Lớp fixture này nằm cùng
 * package nên gọi được constructor {@code protected}, rồi gán field bằng reflection. Đổi ngược
 * lại (thêm setter vào entity chỉ để test ghi được) sẽ mở một đường ghi mà production không
 * được phép dùng.</p>
 */
public final class MonitoringRuleFixtures {

    private MonitoringRuleFixtures() {
    }

    /** p6 §6.9.5 R4 — mặc định {@code streak = 2}, {@code maxConfidence = 0.50}, cooldown 24h. */
    public static MonitoringRule lowQualityStreak(int streak, double maxConfidence, Instant now) {
        return rule(MonitoringRuleCode.LOW_QUALITY_STREAK.name(),
                Map.of("streak", streak, "maxConfidence", maxConfidence),
                HealthFlagSeverity.INFO, 24, "insight.flag.low_quality_streak", now);
    }

    public static MonitoringRule rule(String code, Map<String, Object> params,
                                      HealthFlagSeverity severity, int cooldownHours,
                                      String messageKey, Instant now) {
        MonitoringRule rule = new MonitoringRule();
        set(rule, "code", code);
        set(rule, "name", code);
        set(rule, "enabled", true);
        set(rule, "params", params);
        set(rule, "severity", severity);
        set(rule, "cooldownHours", cooldownHours);
        set(rule, "requiresCalibratedChart", false);
        set(rule, "messageKey", messageKey);
        set(rule, "pushEnabled", false);
        set(rule, "sortOrder", 1);
        set(rule, "createdAt", now);
        set(rule, "updatedAt", now);
        return rule;
    }

    private static void set(MonitoringRule rule, String fieldName, Object value) {
        try {
            Field field = MonitoringRule.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(rule, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Khong gan duoc MonitoringRule." + fieldName, e);
        }
    }
}
