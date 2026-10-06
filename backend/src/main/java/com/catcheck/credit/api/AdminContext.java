package com.catcheck.credit.api;

import com.catcheck.credit.application.AdminActionContext;
import com.catcheck.shared.security.AdminGuard;
import com.catcheck.shared.security.SecurityPrincipal;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Set;

/**
 * Dựng {@link AdminActionContext} từ principal + request hiện tại, và <b>kiểm {@code reason}
 * tại cùng chỗ đó</b> — p8 §8.3.2 ký hiệu {@code Rsn}, p15 REQ-AUD-03.
 *
 * <p>Gộp hai việc vào một lời gọi là có chủ ý: nếu tách thì một endpoint mới có thể dựng context
 * mà quên kiểm {@code reason}, và lỗi đó chỉ lộ ra khi đọc lại {@code audit_log} nhiều tháng sau
 * (lúc cần nó nhất).</p>
 */
final class AdminContext {

    /** Vai trò admin, dùng để chụp {@code audit_log.actor_role} (p4 §4.6.3). */
    private static final List<String> ADMIN_ROLE_PRIORITY =
            List.of("ADMIN_SUPER", "DPO", "ADMIN_SUPPORT", "ADMIN_CATALOG");

    private AdminContext() {
    }

    static AdminActionContext of(SecurityPrincipal principal, String reason, HttpServletRequest request) {
        return build(principal, AdminGuard.requireReason(reason), request);
    }

    /**
     * Cho hành động <b>không</b> mang ký hiệu {@code Rsn} — L9
     * ({@code GET /admin/users/{userId}/credits}) là endpoint chỉ đọc duy nhất ở đây: cột Auth của
     * p8 L9 là {@code R:... · Aud}, không có {@code Rsn}. Vẫn ghi {@code audit_log} (cột
     * {@code Aud} bật), chỉ là không có lý do bắt buộc.
     */
    static AdminActionContext withoutReason(SecurityPrincipal principal, HttpServletRequest request) {
        return build(principal, null, request);
    }

    private static AdminActionContext build(
            SecurityPrincipal principal, String reason, HttpServletRequest request) {
        return new AdminActionContext(
                principal.userId(),
                actorRole(principal),
                reason,
                request.getHeader("X-Request-Id"),
                request.getRemoteAddr(),
                request.getHeader("User-Agent"));
    }

    /**
     * Một tài khoản có thể giữ nhiều vai trò; {@code audit_log.actor_role VARCHAR(32)} chỉ chứa
     * một. Chọn vai trò <b>mạnh nhất</b> theo {@link #ADMIN_ROLE_PRIORITY}: đó là vai trò đã
     * cho phép hành động này đi qua, nên ghi nó mới trả lời được câu "ai được làm việc này".
     */
    private static String actorRole(SecurityPrincipal principal) {
        Set<String> roles = principal.roles();
        for (String candidate : ADMIN_ROLE_PRIORITY) {
            if (AdminGuard.hasAnyRole(principal, Set.of(candidate))) {
                return candidate;
            }
        }
        return roles.stream().findFirst().orElse(null);
    }
}
