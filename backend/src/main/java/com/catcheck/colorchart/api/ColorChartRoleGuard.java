package com.catcheck.colorchart.api;

import com.catcheck.shared.error.PermissionDeniedException;
import com.catcheck.shared.security.SecurityPrincipal;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ColorChartRoleGuard {

    public static final Set<String> CHART_ADMIN_ROLES = Set.of("ADMIN_SUPER", "ADMIN_CATALOG");

    private static final String ROLE_PREFIX = "ROLE_";

    public void requireChartAdmin(SecurityPrincipal principal) {
        boolean allowed = principal.roles().stream()
                .anyMatch(role -> CHART_ADMIN_ROLES.contains(role) || CHART_ADMIN_ROLES.contains(ROLE_PREFIX + role));
        if (!allowed) {
            throw new PermissionDeniedException(ColorChartErrorCode.ADMIN_ROLE_REQUIRED,
                    String.join(",", CHART_ADMIN_ROLES));
        }
    }
}
