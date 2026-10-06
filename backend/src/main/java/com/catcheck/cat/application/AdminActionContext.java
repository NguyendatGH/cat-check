package com.catcheck.cat.application;

import com.catcheck.audit.api.AuditActor;

import java.util.Set;
import java.util.UUID;

/**
 * Ai đang làm, với vai trò nào, vì lý do gì, từ request nào — đủ để dựng một dòng
 * {@code audit_log} cho hành động admin trên hồ sơ mèo (p11 §11.11.1, p15 REQ-AUD-03).
 *
 * <p>Bản song song của {@code credit.application.AdminActionContext} và
 * {@code scan.application.AdminActionContext}; cố ý không dùng chung (đưa vào {@code shared} sẽ
 * buộc {@code shared} biết về {@code audit.api}). Mang thêm {@link #roles()} vì L4 phải phân
 * biệt {@code DPO} với {@code ADMIN_SUPPORT} <b>ở tầng nghiệp vụ</b>, không chỉ ở tầng kiểm
 * quyền.</p>
 *
 * @param adminId   {@code audit_log.actor_id}
 * @param adminRole vai trò mạnh nhất đang giữ — snapshot để nhật ký còn đọc được sau khi vai trò
 *                  bị thu hồi (p4 §4.6.3)
 * @param roles     toàn bộ vai trò của phiên, dạng không tiền tố {@code ROLE_} (p4 §4.4.3)
 * @param reason    lý do đã qua {@code AdminGuard.requireReason}
 * @param requestId tương quan với log ứng dụng
 * @param ipAddress IP socket
 * @param userAgent header {@code User-Agent}
 */
public record AdminActionContext(
        UUID adminId,
        String adminRole,
        Set<String> roles,
        String reason,
        String requestId,
        String ipAddress,
        String userAgent
) {

    public AdminActionContext {
        if (adminId == null) {
            throw new IllegalArgumentException("adminActionContext.adminId bắt buộc");
        }
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    /**
     * {@code actor_type = 'DPO'} khi người thao tác giữ vai trò DPO, ngược lại {@code 'ADMIN'} —
     * {@code audit_log.actor_type} có đủ cả hai giá trị (p4 §4.4.3). Phân biệt ở đây để bản kiểm
     * tra tuân thủ p15 truy được "DPO nào đã xem hồ sơ của ai" mà không phải suy từ
     * {@code actor_role}.
     */
    public AuditActor auditActor() {
        return isDpo() ? AuditActor.dpo(adminId, adminRole) : AuditActor.admin(adminId, adminRole);
    }

    /** Phiên này có giữ vai trò {@code DPO} không — điều kiện cần (chưa đủ) để xem đầy đủ. */
    public boolean isDpo() {
        return roles.contains("DPO") || roles.contains("ROLE_DPO");
    }
}
