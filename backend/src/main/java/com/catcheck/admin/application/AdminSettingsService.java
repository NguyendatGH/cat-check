package com.catcheck.admin.application;

import com.catcheck.admin.api.AdminOpsErrorCode;
import com.catcheck.admin.domain.AppSettingRow;
import com.catcheck.admin.domain.port.AppSettingAdminPort;
import com.catcheck.audit.api.AuditActor;
import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.audit.api.AuditSubjectType;
import com.catcheck.shared.api.AdminETag;
import com.catcheck.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AdminSettingsService {

    private final AppSettingAdminPort settings;
    private final AuditLogService auditLogService;

    public AdminSettingsService(AppSettingAdminPort settings, AuditLogService auditLogService) {
        this.settings = settings;
        this.auditLogService = auditLogService;
    }

    public List<AppSettingRow> listMasked() {
        return settings.findAll().stream().map(AppSettingRow::masked).toList();
    }

    public static String etagOf(AppSettingRow row) {
        return AdminETag.of(row.updatedAt(), row.key());
    }

    @Transactional
    public AppSettingRow update(String key, String newValue, String ifMatch, UUID adminId,
                                String adminRole, String reason, String requestId,
                                String ipAddress, String userAgent) {
        AppSettingRow current = settings.findByKey(key)
                .orElseThrow(() -> new NotFoundException(AdminOpsErrorCode.SETTING_KEY_UNKNOWN, key));
        AdminETag.requireMatch(ifMatch, etagOf(current));

        AppSettingRow updated = settings.updateValue(key, newValue, adminId)
                .orElseThrow(() -> new NotFoundException(AdminOpsErrorCode.SETTING_KEY_UNKNOWN, key));

        AppSettingRow maskedBefore = current.masked();
        AppSettingRow maskedAfter = updated.masked();
        auditLogService.record(AuditEvent.builder()
                .actor(AuditActor.admin(adminId, adminRole))
                .subject(AuditSubjectType.SYSTEM, null)
                .action("ADMIN_SETTING_UPDATED")
                .requestId(requestId).ipAddress(ipAddress).userAgent(userAgent)
                .meta("key", key)
                .meta("valueType", updated.valueType())
                .meta("secret", Boolean.TRUE.equals(updated.secret()))
                .meta("reason", reason)
                .before(java.util.Map.of("value", String.valueOf(maskedBefore.value())))
                .after(java.util.Map.of("value", String.valueOf(maskedAfter.value())))
                .build());
        return maskedAfter;
    }
}
