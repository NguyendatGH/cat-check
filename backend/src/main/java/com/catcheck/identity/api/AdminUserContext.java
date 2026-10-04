package com.catcheck.identity.api;

import com.catcheck.identity.application.AdminUserService;
import com.catcheck.shared.security.AdminGuard;
import com.catcheck.shared.security.SecurityPrincipal;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Set;

/**
 * Dựng {@link AdminUserService.AdminActionContext} và kiểm {@code reason} ở cùng một lời gọi —
 * p8 §8.3.2 ký hiệu {@code Rsn}, p15 REQ-AUD-03.
 *
 * <p>Bản song song của {@code credit.api.AdminContext}. Cố ý không dùng chung: hợp đồng
 * {@code AdminActionContext} là của từng module (module {@code identity} còn phân biệt
 * {@code actor_type = 'DPO'}), và chia sẻ sẽ buộc {@code identity} phụ thuộc {@code credit}.</p>
 */
final class AdminUserContext {

    private static final List<String> ADMIN_ROLE_PRIORITY =
            List.of("ADMIN_SUPER", "DPO", "ADMIN_SUPPORT", "ADMIN_CATALOG");

    private AdminUserContext() {
    }

    /** Cho hành động mang ký hiệu {@code Rsn} — thiếu {@code reason} ⇒ {@code 400}. */
    static AdminUserService.AdminActionContext withReason(
            SecurityPrincipal principal, String reason, HttpServletRequest request) {
        return build(principal, AdminGuard.requireReason(reason), request);
    }

    /** Dùng role bắt buộc của hành động làm actor_role khi một admin đồng thời giữ nhiều role. */
    static AdminUserService.AdminActionContext withRole(
            SecurityPrincipal principal, String role, String reason, HttpServletRequest request) {
        return new AdminUserService.AdminActionContext(principal.userId(), role,
                AdminGuard.requireReason(reason), request.getHeader("X-Request-Id"),
                request.getRemoteAddr(), request.getHeader("User-Agent"));
    }

    /**
     * Cho hành động chỉ đọc không mang {@code Rsn} (L1, L2, L11): vẫn ghi {@code audit_log}
     * (cột {@code Aud} của p8 bật cho cả ba) nhưng không có lý do bắt buộc.
     */
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

    /** Vai trò mạnh nhất đang giữ — {@code audit_log.actor_role VARCHAR(32)} chỉ chứa một. */
    private static String actorRole(SecurityPrincipal principal) {
        for (String candidate : ADMIN_ROLE_PRIORITY) {
            if (AdminGuard.hasAnyRole(principal, Set.of(candidate))) {
                return candidate;
            }
        }
        return principal.roles().stream().findFirst().orElse(null);
    }
}
