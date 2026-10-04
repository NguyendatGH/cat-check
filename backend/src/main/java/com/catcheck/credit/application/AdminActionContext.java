package com.catcheck.credit.application;

import com.catcheck.audit.api.AuditActor;

import java.util.UUID;

/**
 * Ai đang làm, vì lý do gì, từ request nào — đủ để dựng một dòng {@code audit_log} cho hành
 * động admin (p11 §11.11.1, p15 REQ-AUD-03).
 *
 * <p>Đặt ở {@code application} chứ không ở {@code api}: mọi service admin của module cần nó, và
 * tầng {@code api} không được là nơi các service nhìn vào (R3 ngược chiều).</p>
 *
 * @param adminId   {@code audit_log.actor_id}
 * @param adminRole snapshot vai trò lúc hành động — {@code audit_log.actor_role}, để nhật ký còn
 *                  đọc được sau khi vai trò bị thu hồi (p4 §4.6.3)
 * @param reason    lý do đã qua kiểm {@code AdminGuard.requireReason}; {@code null} với hành
 *                  động không mang ký hiệu {@code Rsn}
 * @param requestId tương quan với log ứng dụng
 * @param ipAddress IP socket
 * @param userAgent header {@code User-Agent}
 */
public record AdminActionContext(
        UUID adminId,
        String adminRole,
        String reason,
        String requestId,
        String ipAddress,
        String userAgent
) {

    public AdminActionContext {
        if (adminId == null) {
            throw new IllegalArgumentException("adminActionContext.adminId bắt buộc");
        }
    }

    /**
     * {@code actor_type = 'ADMIN'} cho mọi hành động ở đây.
     *
     * <p>Không phân biệt {@code 'DPO'}: không endpoint nào trong L19–L26 thuộc DPO (ma trận
     * p11 §11.5.4 — DPO không thấy màn mã kích hoạt lẫn màn cấu hình gói).</p>
     */
    public AuditActor auditActor() {
        return AuditActor.admin(adminId, adminRole);
    }
}
