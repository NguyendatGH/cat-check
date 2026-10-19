package com.catcheck.insight.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record UpdateMonitoringRuleRequest(
        Boolean enabled,
        Map<String, Object> params,
        @Min(0) Integer cooldownHours,
        Boolean pushEnabled,
        @NotBlank @Size(min = 10, max = 500) String reason
) {
}
