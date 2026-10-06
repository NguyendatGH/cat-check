package com.catcheck.scan.application;

import com.catcheck.audit.api.AuditActor;

import java.util.Set;
import java.util.UUID;

/**
 * Ai đang làm, với vai trò nào, vì lý do gì, từ request nào — đủ để dựng một dòng
 * {@code audit_log} cho hành động admin trên dữ liệu quét (p11 §11.11.1, p15 REQ-AUD-03).
 *
 * <p>Bản song song của {@code credit.application.AdminActionContext} và
 * {@code identity.api.AdminUserContext}. Cố ý <b>không</b> dùng chung: đưa record này vào
 * {@code shared} sẽ buộc {@code shared} biết về {@code audit.api}, và mỗi module cần một hình
 * dạng hơi khác — bản này mang thêm {@link #roles()} vì hai endpoint L5/L6 phải phân biệt
 * {@code DPO} với {@code ADMIN_SUPPORT} <b>ở tầng nghiệp vụ</b>, không chỉ ở tầng kiểm quyền.</p>
 *
 * @param adminId   {@code audit_log.actor_id}
 * @param adminRole vai trò mạnh nhất đang giữ — {@code audit_log.actor_role VARCHAR(32)} chỉ
 *                  chứa một; snapshot để nhật ký còn đọc được sau khi vai trò bị thu hồi
 *                  (p4 §4.6.3)
 * @param roles     toàn bộ vai trò của phiên, dạng không tiền tố {@code ROLE_} (p4 §4.4.3)
 * @param reason    lý do đã qua {@code AdminGuard.requireReason}; {@code null} với hành động
 *                  không mang ký hiệu {@code Rsn}
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
     * {@code actor_type = 'DPO'} khi người thao tác giữ vai trò DPO, ngược lại {@code 'ADMIN'}.
     *
     * <p>Phân biệt ở đây là bắt buộc, không phải trang trí: L6 (ảnh scan) chỉ DPO làm được, và
     * bản kiểm tra tuân thủ p15 cần truy được "DPO nào đã mở ảnh của ai" mà không phải suy ra từ
     * {@code actor_role}. {@code audit_log.actor_type} có đủ cả hai giá trị (p4 §4.4.3).</p>
     */
    public AuditActor auditActor() {
        return isDpo() ? AuditActor.dpo(adminId, adminRole) : AuditActor.admin(adminId, adminRole);
    }

    /** Phiên này có giữ vai trò {@code DPO} không — điều kiện cần (chưa đủ) của L4/L5/L6. */
    public boolean isDpo() {
        return roles.contains("DPO") || roles.contains("ROLE_DPO");
    }
}
