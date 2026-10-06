package com.catcheck.cat.api;

import com.catcheck.cat.application.AdminActionContext;
import com.catcheck.shared.security.AdminGuard;
import com.catcheck.shared.security.SecurityPrincipal;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Set;

/**
 * Dựng {@link AdminActionContext} và kiểm {@code reason} <b>trong cùng một lời gọi</b> — p8
 * §8.3.2 ký hiệu {@code Rsn}, p15 REQ-AUD-03.
 *
 * <p>Gộp hai việc là có chủ ý (cùng lập luận như {@code credit.api.AdminContext}): nếu tách thì
 * một endpoint mới có thể dựng context mà quên kiểm {@code reason}, và lỗi đó chỉ lộ ra khi đọc
 * lại {@code audit_log} nhiều tháng sau — đúng lúc cần nó nhất.</p>
 */
final class AdminCatContext {

    /** Vai trò admin, để chụp {@code audit_log.actor_role} (p4 §4.6.3). */
    private static final List<String> ADMIN_ROLE_PRIORITY =
            List.of("ADMIN_SUPER", "DPO", "ADMIN_SUPPORT", "ADMIN_CATALOG");

    private AdminCatContext() {
    }

    /** Cho hành động mang ký hiệu {@code Rsn} — thiếu {@code reason} ⇒ {@code 400}. */
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

    /**
     * Vai trò <b>mạnh nhất</b> đang giữ — {@code audit_log.actor_role VARCHAR(32)} chỉ chứa một.
     * Toàn bộ danh sách vai trò vẫn đi theo {@link AdminActionContext#roles()} vì L4 còn phải
     * phân biệt DPO ở tầng nghiệp vụ.
     */
    private static String actorRole(SecurityPrincipal principal) {
        for (String candidate : ADMIN_ROLE_PRIORITY) {
            if (AdminGuard.hasAnyRole(principal, Set.of(candidate))) {
                return candidate;
            }
        }
        return principal.roles().stream().findFirst().orElse(null);
    }
}
