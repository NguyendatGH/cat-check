package com.catcheck.identity.api.dto;

import com.catcheck.identity.application.AdminUserService;

import java.time.Instant;
import java.util.UUID;

/**
 * L3 {@code POST /admin/users/{userId}/unmask} — giá trị thật, trả đúng một lần.
 *
 * <p>{@code unmaskedUntil} là mốc UI phải tự che lại (p15 REQ-RBAC-01: tự khôi phục mask sau
 * 15 phút). Server không giữ trạng thái unmask nào, nên L2 gọi sau đó vẫn trả bản mask và muốn
 * xem tiếp phải bấm lần nữa — xem handoff H15.104.</p>
 *
 * <p>Response này có {@code Cache-Control: no-store} ở controller: nó là endpoint duy nhất trả
 * PII thô của một người KHÁC người đang đăng nhập.</p>
 */
public record AdminUnmaskResponse(UUID userId, String email, String phone, Instant unmaskedUntil) {

    public static AdminUnmaskResponse from(AdminUserService.UnmaskedPii pii) {
        return new AdminUnmaskResponse(pii.userId(), pii.email(), pii.phone(), pii.unmaskedUntil());
    }
}
