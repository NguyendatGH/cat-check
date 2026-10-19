package com.catcheck.shop.api;

import com.catcheck.shared.error.PermissionDeniedException;
import com.catcheck.shared.security.SecurityPrincipal;

import java.util.Set;

/** Shop staff roles shared by the admin controllers. */
final class ShopAdminRole {
    private static final Set<String> ROLES = Set.of("ADMIN_SUPER", "ADMIN_CATALOG");

    private ShopAdminRole() { }

    static String require(SecurityPrincipal user) {
        if (user != null) {
            for (String r : user.roles()) {
                String n = r.startsWith("ROLE_") ? r.substring(5) : r;
                if (ROLES.contains(n)) return n;
            }
        }
        throw new PermissionDeniedException(ShopErrorCode.ADMIN_ROLE_REQUIRED, String.join(",", ROLES));
    }
}
