package com.catcheck.insight.api.dto;

import com.catcheck.insight.domain.MonitoringRule;

import java.time.Instant;
import java.util.Map;

/**
 * L38 — một rule trong màn cấu hình cảnh báo (p8 §8.4.12 L38).
 *
 * <p>Khác hẳn {@link MonitoringRuleResponse} (F4, cho người dùng cuối): ở đây trả <b>đủ</b> cột
 * nội bộ của {@code monitoring_rule} (p4 D11) vì đó chính là thứ admin đang cấu hình. Lý do F4
 * cố ý che {@code params} là để người dùng không suy luận ngược ngưỡng cảnh báo — lý do đó
 * không áp cho chính người đặt ngưỡng.</p>
 *
 * @param etag ETag của dòng, để L39 dùng làm {@code If-Match} mà không phải gọi thêm một GET
 */
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
