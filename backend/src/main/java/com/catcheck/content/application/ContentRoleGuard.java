package com.catcheck.content.application;

import com.catcheck.content.api.ContentErrorCode;
import com.catcheck.shared.error.PermissionDeniedException;
import com.catcheck.shared.security.SecurityPrincipal;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Kiểm tra vai trò quản trị nội dung theo p8 §8.3.2 (ký hiệu {@code R:ADMIN_SUPER,ADMIN_CATALOG,DPO}).
 *
 * <p>Vai trò lưu trong {@code user_role.role} KHÔNG có tiền tố {@code ROLE_} (p4 §4.4.3: {@code USER |
 * ADMIN_SUPPORT | ADMIN_CATALOG | ADMIN_SUPER | DPO}), nhưng {@code RoleConstants} của {@code shared}
 * lại dùng dạng {@code ROLE_*} cho các hằng vai trò chung. Hai nơi khác nhau, và module identity
 * (A1) là chủ sở hữu {@code SecurityPrincipal.roles()} nên A3 không tự chốt cách ghi.
 *
 * <p>Vì vậy lớp này chấp nhận CẢ HAI dạng: so khớp theo tên trần, và nếu không có thì thử lại với
 * tiền tố {@code ROLE_}. Cách này sai ở cả hai phía thay vì chỉ sai ở một phía, và không cần sửa
 * {@code shared}. Xem {@code docs/handovers/A3.md} mục "Cần A1 xác nhận".</p>
 */
@Component
public class ContentRoleGuard {

    /** p4 §4.4.3 + p8 §8.3.2 — vai trò được phép quản trị nội dung. */
    public static final Set<String> CONTENT_ADMIN_ROLES =
            Set.of("ADMIN_SUPER", "ADMIN_CATALOG", "DPO");

    /** L44 thêm DPO: người phụ trách pháp lý cũng duyệt được bài. */
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
