package com.catcheck.credit.api;

import com.catcheck.credit.application.AdminActionContext;
import com.catcheck.shared.security.AdminGuard;
import com.catcheck.shared.security.SecurityPrincipal;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Set;

final class AdminContext {

    private static final List<String> ADMIN_ROLE_PRIORITY =
            List.of("ADMIN_SUPER", "DPO", "ADMIN_SUPPORT", "ADMIN_CATALOG");

    private AdminContext() {
    }

    static AdminActionContext of(SecurityPrincipal principal, String reason, HttpServletRequest request) {
        return build(principal, AdminGuard.requireReason(reason), request);
    }

    static AdminActionContext withoutReason(SecurityPrincipal principal, HttpServletRequest request) {
        return build(principal, null, request);
    }

    private static AdminActionContext build(
            SecurityPrincipal principal, String reason, HttpServletRequest request) {
        return new AdminActionContext(
                principal.userId(),
                actorRole(principal),
                reason,
                request.getHeader("X-Request-Id"),
                request.getRemoteAddr(),
                request.getHeader("User-Agent"));
    }

    private static String actorRole(SecurityPrincipal principal) {
        Set<String> roles = principal.roles();
        for (String candidate : ADMIN_ROLE_PRIORITY) {
            if (AdminGuard.hasAnyRole(principal, Set.of(candidate))) {
                return candidate;
            }
        }
        return roles.stream().findFirst().orElse(null);
    }
}
