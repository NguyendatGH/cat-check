package com.catcheck.admin.application;

import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.shared.application.spi.AppSettingWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** L71 — điều khiển banner bảo trì công khai qua app_setting. */
@Service
public class AdminMaintenanceService {

    private static final String ACTIVE_KEY = "app.maintenance_active";
    private static final String UNTIL_KEY = "app.maintenance_until";
    private final AppSettingWriter writer;
    private final AuditLogService auditLogService;

    public AdminMaintenanceService(AppSettingWriter writer, AuditLogService auditLogService) {
        this.writer = writer;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public Result update(boolean active, String until, UUID adminId, String adminRole, String reason,
                         String requestId, String ipAddress, String userAgent) {
        AppSettingWriter.Setting activeSetting = writer.updateBoolean(ACTIVE_KEY, active,
                "Bật banner/chế độ bảo trì công khai.", adminId);
        AppSettingWriter.Setting untilSetting = writer.updateString(UNTIL_KEY, until == null ? "" : until,
                "Mốc dự kiến kết thúc bảo trì dạng ISO-8601.", adminId);
        auditLogService.record(AuditEvent.builder()
                .actor(com.catcheck.audit.api.AuditActor.admin(adminId, adminRole))
                .subject(com.catcheck.audit.api.AuditSubjectType.SYSTEM, null)
                .action("ADMIN_MAINTENANCE_UPDATED")
                .requestId(requestId).ipAddress(ipAddress).userAgent(userAgent)
                .meta("active", active).meta("until", until).meta("reason", reason).build());
        return new Result(activeSetting.value(), untilSetting.value(), activeSetting.updatedAt());
    }

    public record Result(String active, String until, Instant updatedAt) {
    }
}
