package com.catcheck.insight.api.dto;

import java.util.List;

/** F4 — {@code GET /reference/monitoring-rules}. Bọc {@code items} giống F1/F2/F3. */
public record MonitoringRuleListResponse(List<MonitoringRuleResponse> items) {
}
