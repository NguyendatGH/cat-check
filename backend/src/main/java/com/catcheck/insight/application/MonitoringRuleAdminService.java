package com.catcheck.insight.application;

import com.catcheck.audit.api.AuditActor;
import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.audit.api.AuditSubjectType;
import com.catcheck.insight.api.InsightErrorCode;
import com.catcheck.insight.domain.MonitoringRule;
import com.catcheck.insight.domain.port.MonitoringRuleRepository;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * L38, L39 — cấu hình rule cảnh báo phía quản trị (p8 §8.4.12 mục (c)).
 *
 * <p>Bảng {@code monitoring_rule} là <b>cấu hình toàn hệ thống</b>: đổi một dòng ở đây đổi hành
 * vi cảnh báo cho mọi con mèo của mọi người dùng. Vì vậy L39 đi kèm {@code If-Match} (kiểm ở
 * tầng api), {@code reason} bắt buộc và {@code audit_log} trong cùng transaction — đúng ba thứ
 * mà p8 §8.1.11 và p15 REQ-AUD-03 đòi cho endpoint ghi cấu hình admin.</p>
 */
@Service
public class MonitoringRuleAdminService {

    /** Vai trò ghi {@code audit_log.actor_role}: L39 chỉ {@code ADMIN_SUPER} (p8 §8.4.12 L39). */
    public static final String ACTOR_ROLE = "ADMIN_SUPER";

    private final MonitoringRuleRepository ruleRepository;
    private final AuditLogService auditLog;
    private final Clock clock;

    public MonitoringRuleAdminService(MonitoringRuleRepository ruleRepository,
                                      AuditLogService auditLog,
                                      Clock clock) {
        this.ruleRepository = ruleRepository;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ L38

    @Transactional(readOnly = true)
    public List<MonitoringRule> listForAdmin() {
        return ruleRepository.findAllForAdmin();
    }

    @Transactional(readOnly = true)
    public MonitoringRule require(String code) {
        return ruleRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException(InsightErrorCode.MONITORING_RULE_NOT_FOUND, code));
    }

    // ------------------------------------------------------------------ L39

    /**
     * Sửa một rule. Trường {@code null} = không đổi (merge-patch, p8 §8.1.11).
     *
     * @param params tham số mới; phải là map phẳng gồm giá trị vô hướng — một map lồng ghi được
     *               vào JSONB nhưng {@code MonitoringRule.paramAsInt/Double} sẽ đọc ra giá trị
     *               mặc định một cách im lặng, nghĩa là rule chạy sai mà không ai biết
     */
    @Transactional
    public MonitoringRule update(String code, Boolean enabled, Map<String, Object> params,
                                 Integer cooldownHours, Boolean pushEnabled, RuleAdminAction action) {
        MonitoringRule rule = require(code);
        validateParams(params);
        Map<String, Object> before = snapshot(rule);
        Instant now = clock.instant();
        try {
            rule.applyAdminUpdate(enabled, params, cooldownHours, pushEnabled, now);
        } catch (IllegalArgumentException ex) {
            // Bất biến của p4 D11 (cooldown >= 0, cooldown = 0 chỉ cho URGENT_CLINICAL_SIGN).
            // Cú pháp đúng, ràng buộc nghiệp vụ sai ⇒ 422 (p8 §8.1.12).
            throw new BusinessRuleException(InsightErrorCode.MONITORING_RULE_INVALID, ex.getMessage());
        }
        MonitoringRule saved = ruleRepository.saveAndReload(rule);
        auditLog.record(AuditEvent.builder()
                .actor(AuditActor.admin(action.actorId(), ACTOR_ROLE))
                .subject(AuditSubjectType.SETTING, null)
                .action("ADMIN_MONITORING_RULE_UPDATED")
                .metadata(Map.of("ruleCode", code, "reason", action.reason()))
                .before(before)
                .after(snapshot(saved))
                .requestId(action.requestId())
                .ipAddress(action.ipAddress())
                .userAgent(action.userAgent())
                .build());
        return saved;
    }

    private void validateParams(Map<String, Object> params) {
        if (params == null) {
            return;
        }
        if (params.isEmpty()) {
            // monitoring_rule.params là NOT NULL và mỗi rule đọc tham số của mình từ đó; map
            // rỗng làm mọi rule rơi về giá trị mặc định hard-code trong Java — ngược hẳn với
            // "rule là dữ liệu cấu hình" của p6 §6.9.
            throw new BusinessRuleException(InsightErrorCode.MONITORING_RULE_INVALID, "params");
        }
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            Object value = entry.getValue();
            boolean scalar = value instanceof Number || value instanceof Boolean || value instanceof String;
            if (!scalar) {
                throw new BusinessRuleException(InsightErrorCode.MONITORING_RULE_INVALID,
                        "params." + entry.getKey());
            }
        }
    }

    /** Ảnh {@code before}/{@code after} cho {@code audit_log} — không có PII, chỉ cấu hình. */
    private static Map<String, Object> snapshot(MonitoringRule rule) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("enabled", rule.isEnabled());
        values.put("cooldownHours", rule.getCooldownHours());
        values.put("pushEnabled", rule.isPushEnabled());
        values.put("params", rule.getParams());
        return values;
    }

    /**
     * Ai làm, vì sao, từ đâu — p4 §4.6.3 + p15 REQ-AUD-03.
     *
     * @param actorId   người thực hiện
     * @param reason    lý do đã {@code strip()}, ≥ 10 ký tự (kiểm ở tầng api)
     * @param requestId {@code X-Request-Id} (p8 §8.1.9)
     * @param ipAddress IP nguồn
     * @param userAgent User-Agent nguồn
     */
    public record RuleAdminAction(UUID actorId, String reason, String requestId,
                                  String ipAddress, String userAgent) {
    }
}
