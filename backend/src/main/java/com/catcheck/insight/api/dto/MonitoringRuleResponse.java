package com.catcheck.insight.api.dto;

import com.catcheck.insight.domain.MonitoringRule;

/**
 * F4 — một rule trong {@code GET /reference/monitoring-rules} (p8 §8.4.6).
 *
 * <p>Mục đích của endpoint theo p8 là giải thích cho người dùng "vì sao tôi bị flag", nên chỉ
 * trả phần GIẢI THÍCH, cố ý BỎ các cột nội bộ của {@code monitoring_rule} (p4 D11):</p>
 * <ul>
 *   <li>{@code name} — p4 ghi rõ "Hiển thị cho admin", không phải nhãn cho người dùng cuối.</li>
 *   <li>{@code params} — tham số điều chỉnh thuật toán ({@code windowHours}, {@code minCount},
 *       {@code minDelta}…). Lộ ra là mời người dùng suy luận ngược ngưỡng cảnh báo, và ngưỡng
 *       còn đổi theo hiệu chuẩn.</li>
 *   <li>{@code cooldownHours}, {@code pushEnabled}, {@code requiresCalibratedChart} — hành vi
 *       nội bộ của bộ máy, không phải thông tin người dùng cần.</li>
 * </ul>
 *
 * <p>{@code messageKey} là khoá i18n chứ không phải câu chữ: quyết định #15 (p4 D11) cấm lưu
 * text cứng trong DB — client tự resolve theo locale.</p>
 */
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
