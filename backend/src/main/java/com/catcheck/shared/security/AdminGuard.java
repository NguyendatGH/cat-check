package com.catcheck.shared.security;

import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.PermissionDeniedException;

import java.util.Set;

public final class AdminGuard {

    public static final int REASON_MIN_LENGTH = 10;

    private static final String ROLE_PREFIX = "ROLE_";

    private AdminGuard() {
    }

    public static boolean hasAnyRole(SecurityPrincipal principal, Set<String> allowed) {
        if (principal == null) {
            return false;
        }
        return principal.roles().stream().anyMatch(role ->
                allowed.contains(role) || allowed.contains(ROLE_PREFIX + role)
                        || (role.startsWith(ROLE_PREFIX) && allowed.contains(role.substring(ROLE_PREFIX.length()))));
    }

    public static void requireAnyRole(SecurityPrincipal principal, Set<String> allowed) {
        if (!hasAnyRole(principal, allowed)) {
            throw new PermissionDeniedException(AdminApiErrorCode.ACCESS_DENIED,
                    String.join(", ", allowed.stream().sorted().toList()));
        }
    }

    public static String requireReason(String reason) {
        String trimmed = reason == null ? "" : reason.strip();
        if (trimmed.length() < REASON_MIN_LENGTH) {
            throw new BusinessRuleException(AdminApiErrorCode.REASON_REQUIRED, REASON_MIN_LENGTH);
        }
        return trimmed;
    }
}
