package com.catcheck.insight.api.dto;

import com.catcheck.insight.domain.MonitoringRule;

public record MonitoringRuleResponse(
        String code,
        String severity,
        String messageKey,
        int sortOrder
) {

    public static MonitoringRuleResponse from(MonitoringRule rule) {
        return new MonitoringRuleResponse(
                rule.getCode(),
                rule.getSeverity().name(),
                rule.getMessageKey(),
                rule.getSortOrder());
    }
}
