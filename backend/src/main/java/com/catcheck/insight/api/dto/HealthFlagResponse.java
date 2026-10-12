package com.catcheck.insight.api.dto;

import com.catcheck.insight.domain.HealthFlag;

import java.time.Instant;
import java.util.Map;

public record HealthFlagResponse(
        String flagId,
        String catId,
        String ruleCode,
        String severity,
        String triggerScanId,
        Instant triggeredAt,
        Instant windowFrom,
        Instant windowTo,
        String messageKey,
        Map<String, Object> messageParams,
        String explanationVi,
        Instant acknowledgedAt,
        boolean acknowledged,
        String disclaimerKey
) {

    public static HealthFlagResponse from(HealthFlag flag) {
        return new HealthFlagResponse(
                flag.getId().toString(),
                flag.getCatId().toString(),
                flag.getRuleCode(),
                flag.getSeverity().name(),
                flag.getTriggerScanId() == null ? null : flag.getTriggerScanId().toString(),
                flag.getTriggeredAt(),
                flag.getWindowFrom(),
                flag.getWindowTo(),
                flag.getMessageKey(),
                flag.getMessageParams(),
                flag.getExplanationVi(),
                flag.getAcknowledgedAt(),
                flag.getAcknowledgedAt() != null,
                "legal.disclaimer.short");
    }
}
