package com.catcheck.cat.application;

import com.catcheck.audit.api.AuditActor;

import java.util.Set;
import java.util.UUID;

public record AdminActionContext(
        UUID adminId,
        String adminRole,
        Set<String> roles,
        String reason,
        String requestId,
        String ipAddress,
        String userAgent
) {

    public AdminActionContext {
        if (adminId == null) {
            throw new IllegalArgumentException("adminActionContext.adminId bắt buộc");
        }
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public AuditActor auditActor() {
        return isDpo() ? AuditActor.dpo(adminId, adminRole) : AuditActor.admin(adminId, adminRole);
    }

    public boolean isDpo() {
        return roles.contains("DPO") || roles.contains("ROLE_DPO");
    }
}
