package com.catcheck.insight.api.dto;

import com.catcheck.insight.domain.MonitoringRule;

import java.time.Instant;
import java.util.Map;

public record AdminMonitoringRuleResponse(
        String code,
        String name,
        Boolean enabled,
        Map<String, Object> params,
        String severity,
        Integer cooldownHours,
        Boolean requiresCalibratedChart,
        String messageKey,
        Boolean pushEnabled,
        Integer sortOrder,
        Instant updatedAt,
        String etag
) {

    public static AdminMonitoringRuleResponse from(MonitoringRule rule, String etag) {
        return new AdminMonitoringRuleResponse(
                rule.getCode(),
                rule.getName(),
                rule.isEnabled(),
                rule.getParams(),
                rule.getSeverity().name(),
                rule.getCooldownHours(),
                rule.isRequiresCalibratedChart(),
                rule.getMessageKey(),
                rule.isPushEnabled(),
                rule.getSortOrder(),
                rule.getUpdatedAt(),
                etag);
    }
}
