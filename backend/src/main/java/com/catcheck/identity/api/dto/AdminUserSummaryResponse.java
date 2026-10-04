package com.catcheck.identity.api.dto;

import com.catcheck.identity.domain.AdminUserSummary;
import com.catcheck.shared.security.PiiMask;

import java.time.Instant;
import java.util.UUID;

/**
 * Một dòng của L1 {@code GET /admin/users} — p14 §14.3.2 mục 2.
 *
 * <p>{@code emailMasked} là trường DUY NHẤT mang email, và nó đã mask (p15 REQ-RBAC-01). Tên
 * trường nói rõ điều đó để không ai lỡ dùng nó như một email thật; muốn bản thật thì gọi L3
 * (có {@code reason}, có audit).</p>
 *
 * <p>{@code fullName} KHÔNG mask: p15 REQ-RBAC-01 nêu hai ví dụ là email và số điện thoại, và
 * màn hỗ trợ khách hàng mất gần hết công dụng nếu không đọc được tên người đang gọi tới. Đây là
 * điểm đã ghi handoff H15.105 để owner/DPO xác nhận.</p>
 */
public record AdminUserSummaryResponse(
        UUID userId,
        String emailMasked,
        String fullName,
        String status,
        String onboardingStatus,
        boolean emailVerified,
        Instant createdAt,
        Instant lastLoginAt,
        Instant lockedUntil,
        long catCount,
        long scanCount,
        String highestPackage
) {

    public static AdminUserSummaryResponse from(AdminUserSummary user) {
        return new AdminUserSummaryResponse(
                user.userId(),
                PiiMask.email(user.email()),
                user.fullName(),
                user.status().name(),
                user.onboardingStatus().name(),
                user.emailVerifiedAt() != null,
                user.createdAt(),
                user.lastLoginAt(),
                user.lockedUntil(),
                user.catCount(),
                user.scanCount(),
                user.highestPackage());
    }
}
