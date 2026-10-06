package com.catcheck.privacy.api;

import com.catcheck.shared.security.MfaLevel;
import com.catcheck.shared.security.SecurityPrincipal;

import java.util.Set;
import java.util.UUID;

/**
 * Principal admin tối giản cho các bài test tầng API của nhóm L.
 *
 * <p>{@code mfaLevel = TOTP} vì mọi {@code /api/v1/admin/**} đã qua {@code AdminMfaGateFilter}
 * trước khi tới controller (p8 §8.4.12 điều kiện chung #1) — nhánh đó có bài riêng
 * {@code AdminMfaGateFilterTest}; ở đây đang kiểm lớp thứ hai, tức vai trò theo từng endpoint
 * mà p14 §14.5.1 mục 2 đòi server kiểm độc lập với UI.</p>
 *
 * <p>Vai trò KHÔNG có tiền tố {@code ROLE_}, đúng dạng {@code user_role.role} của p4 §4.4.3 —
 * {@code AdminGuard} chấp nhận cả hai dạng, nhưng test nên dùng dạng của DB.</p>
 */
record AdminTestPrincipal(UUID userId, String email, Set<String> roles) implements SecurityPrincipal {

    static AdminTestPrincipal of(UUID userId, String role) {
        return new AdminTestPrincipal(userId, "staff@catcheck.vn", Set.of(role));
    }

    @Override
    public MfaLevel mfaLevel() {
        return MfaLevel.TOTP;
    }
}
