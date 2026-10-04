package com.catcheck.identity.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Một dòng của L1 {@code GET /admin/users} — đúng các cột p14 §14.3.2 mục 2 liệt kê.
 *
 * <p><b>Email ở đây vẫn là bản THẬT</b>, chưa mask: mask là việc của tầng dựng response
 * ({@code AdminUserService}) để chỉ có một chỗ quyết định, và để đường unmask (L3) không phải
 * đọc lại DB bằng một truy vấn thứ hai. Không bao giờ log instance này.</p>
 *
 * @param catCount       số hồ sơ mèo — p14 cột "số mèo"
 * @param scanCount      tổng số lần quét — p14 cột "tổng số scan"
 * @param highestPackage gói cao nhất từng kích hoạt ({@code user_entitlement.highest_package},
 *                       p5 R5); {@code null} nếu chưa kích hoạt gói nào
 */
public record AdminUserSummary(
        UUID userId,
        String email,
        String fullName,
        UserStatus status,
        OnboardingStatus onboardingStatus,
        Instant emailVerifiedAt,
        Instant createdAt,
        Instant lastLoginAt,
        Instant lockedUntil,
        long catCount,
        long scanCount,
        String highestPackage
) {
}
