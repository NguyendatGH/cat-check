package com.catcheck.content.application;

import com.catcheck.content.api.ContentErrorCode;
import com.catcheck.shared.error.PermissionDeniedException;
import com.catcheck.shared.security.SecurityPrincipal;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ContentRoleGuard {

    public static final Set<String> CONTENT_ADMIN_ROLES =
            Set.of("ADMIN_SUPER", "ADMIN_CATALOG", "DPO");

    public static final Set<String> PUBLISH_ROLES = Set.of("ADMIN_SUPER", "DPO");

    private static final String ROLE_PREFIX = "ROLE_";

    public void requireContentAdmin(SecurityPrincipal principal) {
        requireAny(principal, CONTENT_ADMIN_ROLES);
    }

    public void requirePublisher(SecurityPrincipal principal) {
        requireAny(principal, PUBLISH_ROLES);
    }

    private void requireAny(SecurityPrincipal principal, Set<String> allowed) {
        Set<String> held = principal == null || principal.roles() == null ? Set.of() : principal.roles();
        boolean permitted = allowed.stream().anyMatch(allowedRole -> matches(held, allowedRole));
        if (!permitted) {
            throw new PermissionDeniedException(ContentErrorCode.ADMIN_ROLE_REQUIRED, String.join(",", allowed));
        }
    }

    private boolean matches(Set<String> held, String allowedRole) {
        return held.contains(allowedRole) || held.contains(ROLE_PREFIX + allowedRole);
    }
}
