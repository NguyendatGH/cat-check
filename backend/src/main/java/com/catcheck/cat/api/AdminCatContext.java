package com.catcheck.cat.api;

import com.catcheck.cat.application.AdminActionContext;
import com.catcheck.shared.security.AdminGuard;
import com.catcheck.shared.security.SecurityPrincipal;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Set;

final class AdminCatContext {

    private static final List<String> ADMIN_ROLE_PRIORITY =
            List.of("ADMIN_SUPER", "DPO", "ADMIN_SUPPORT", "ADMIN_CATALOG");

    private AdminCatContext() {
    }

    static AdminActionContext withReason(
            SecurityPrincipal principal, String reason, HttpServletRequest request) {
        return new AdminActionContext(
                principal.userId(),
                actorRole(principal),
                principal.roles(),
                AdminGuard.requireReason(reason),
                request.getHeader("X-Request-Id"),
                request.getRemoteAddr(),
                request.getHeader("User-Agent"));
    }

    private static String actorRole(SecurityPrincipal principal) {
        for (String candidate : ADMIN_ROLE_PRIORITY) {
            if (AdminGuard.hasAnyRole(principal, Set.of(candidate))) {
                return candidate;
            }
        }
        return principal.roles().stream().findFirst().orElse(null);
    }
}
