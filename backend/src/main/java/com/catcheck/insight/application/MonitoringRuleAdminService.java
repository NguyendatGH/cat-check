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

@Service
public class MonitoringRuleAdminService {

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


    @Transactional(readOnly = true)
    public List<MonitoringRule> listForAdmin() {
        return ruleRepository.findAllForAdmin();
    }

    @Transactional(readOnly = true)
    public MonitoringRule require(String code) {
        return ruleRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException(InsightErrorCode.MONITORING_RULE_NOT_FOUND, code));
    }


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

    private static Map<String, Object> snapshot(MonitoringRule rule) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("enabled", rule.isEnabled());
        values.put("cooldownHours", rule.getCooldownHours());
        values.put("pushEnabled", rule.isPushEnabled());
        values.put("params", rule.getParams());
        return values;
    }

    public record RuleAdminAction(UUID actorId, String reason, String requestId,
                                  String ipAddress, String userAgent) {
    }
}
