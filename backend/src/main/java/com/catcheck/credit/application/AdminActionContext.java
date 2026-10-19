package com.catcheck.credit.application;

import com.catcheck.audit.api.AuditActor;

import java.util.UUID;

public record AdminActionContext(
        UUID adminId,
        String adminRole,
        String reason,
        String requestId,
        String ipAddress,
        String userAgent
) {

    public AdminActionContext {
        if (adminId == null) {
            throw new IllegalArgumentException("adminActionContext.adminId bắt buộc");
        }
    }

    public AuditActor auditActor() {
        return AuditActor.admin(adminId, adminRole);
    }
}
