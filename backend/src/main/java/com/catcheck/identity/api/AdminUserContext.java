package com.catcheck.identity.api;

import com.catcheck.identity.application.AdminUserService;
import com.catcheck.shared.security.AdminGuard;
import com.catcheck.shared.security.SecurityPrincipal;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Set;

final class AdminUserContext {

    private static final List<String> ADMIN_ROLE_PRIORITY =
            List.of("ADMIN_SUPER", "DPO", "ADMIN_SUPPORT", "ADMIN_CATALOG");

    private AdminUserContext() {
    }

    static AdminUserService.AdminActionContext withReason(
            SecurityPrincipal principal, String reason, HttpServletRequest request) {
        return build(principal, AdminGuard.requireReason(reason), request);
    }

    static AdminUserService.AdminActionContext withRole(
            SecurityPrincipal principal, String role, String reason, HttpServletRequest request) {
        return new AdminUserService.AdminActionContext(principal.userId(), role,
                AdminGuard.requireReason(reason), request.getHeader("X-Request-Id"),
                request.getRemoteAddr(), request.getHeader("User-Agent"));
    }

    static AdminUserService.AdminActionContext withoutReason(
            SecurityPrincipal principal, HttpServletRequest request) {
        return build(principal, null, request);
    }

    private static AdminUserService.AdminActionContext build(
            SecurityPrincipal principal, String reason, HttpServletRequest request) {
        return new AdminUserService.AdminActionContext(
                principal.userId(),
                actorRole(principal),
                reason,
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
