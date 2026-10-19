package com.catcheck.credit.application;

import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.audit.api.AuditOutcome;
import com.catcheck.audit.api.AuditSubjectType;
import com.catcheck.credit.api.CreditErrorCode;
import com.catcheck.credit.domain.PackagePlan;
import com.catcheck.credit.domain.PackagePlanAdminView;
import com.catcheck.credit.domain.PackagePlanUpdate;
import com.catcheck.credit.domain.port.PackagePlanPort;
import com.catcheck.shared.error.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PackagePlanAdminService {

    private static final Logger log = LoggerFactory.getLogger(PackagePlanAdminService.class);

    private final PackagePlanPort packagePlanPort;
    private final AuditLogService auditLogService;

    public PackagePlanAdminService(PackagePlanPort packagePlanPort, AuditLogService auditLogService) {
        this.packagePlanPort = packagePlanPort;
        this.auditLogService = auditLogService;
    }


    @Transactional(readOnly = true)
    public List<PackagePlanAdminView> list(boolean includeInactive) {
        return packagePlanPort.findAllForAdmin(includeInactive);
    }

    @Transactional(readOnly = true)
    public PackagePlanAdminView require(String code) {
        return packagePlanPort.findForAdmin(code)
                .orElseThrow(() -> new NotFoundException(CreditErrorCode.PACKAGE_PLAN_NOT_FOUND, code));
    }


    @Transactional
    public PackagePlanAdminView update(String code, PackagePlanUpdate update, AdminActionContext context) {
        PackagePlanAdminView before = require(code);
        if (!update.isEmpty()) {
            packagePlanPort.update(code, update);
        }
        PackagePlanAdminView after = require(code);

        auditLogService.record(AuditEvent.builder()
                .actor(context.auditActor())
                .subject(AuditSubjectType.SETTING, null)
                .action("PACKAGE_PLAN_UPDATE")
                .outcome(AuditOutcome.SUCCESS)
                .meta("packageCode", code)
                .meta("reason", context.reason())
                .before(snapshot(before.plan()))
                .after(snapshot(after.plan()))
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .build());

        log.info("Admin sửa cấu hình gói {}: version {} -> {}",
                code, before.plan().version(), after.plan().version());
        return after;
    }

    private static Map<String, Object> snapshot(PackagePlan plan) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("creditAmount", plan.creditAmount());
        values.put("creditValidityDays", plan.creditValidityDays());
        values.put("maxCatProfiles", plan.maxCatProfiles());
        values.put("active", plan.active());
        values.put("version", plan.version());
        values.put("historyLevel", plan.features().history().name());
        values.put("trend", plan.features().trend());
        values.put("reminder", plan.features().reminder());
        values.put("export", plan.features().export());
        values.put("storeImage", plan.features().storeImage());
        return values;
    }
}
