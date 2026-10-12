package com.catcheck.insight.domain;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;

public final class MonitoringRuleFixtures {

    private MonitoringRuleFixtures() {
    }

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
