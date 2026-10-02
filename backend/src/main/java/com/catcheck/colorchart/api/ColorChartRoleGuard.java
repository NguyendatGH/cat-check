package com.catcheck.colorchart.api;

import com.catcheck.shared.error.PermissionDeniedException;
import com.catcheck.shared.security.SecurityPrincipal;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Kiểm tra vai trò quản trị bảng màu theo p8 §8.3.2 (ký hiệu {@code R:ADMIN_SUPER,ADMIN_CATALOG}).
 *
 * <p>Vai trò lưu trong {@code user_role.role} KHÔNG có tiền tố {@code ROLE_} (p4 §4.4.3), nhưng
 * {@code RoleConstants} của {@code shared} lại dùng dạng {@code ROLE_*}. Như {@code ContentRoleGuard},
 * lớp này chấp nhận CẢ HAI dạng để không phải sửa {@code shared}.
 */
@Component
public class ColorChartRoleGuard {

    /** p4 §4.4.3 + p8 §8.3.2 — vai trò được phép quản trị bảng màu. */
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
