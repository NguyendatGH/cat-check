package com.catcheck.identity.api.dto;

import com.catcheck.identity.application.AdminUserService;
import com.catcheck.identity.domain.AdminUserSummary;
import com.catcheck.shared.security.PiiMask;
import com.catcheck.identity.domain.UserAccount;
import com.catcheck.identity.domain.UserRole;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * L2 {@code GET /admin/users/{userId}} — tab "Tổng quan" của p14 §14.3.2 mục 2.
 *
 * <p>{@code phoneMasked} là {@code null} khi tài khoản không có số điện thoại — khác với
 * {@code "***"} (có số nhưng đang che). Phân biệt được hai thứ đó là điều tổng đài cần: "chưa
 * có số để gọi" và "có số, bấm unmask" dẫn tới hai hành động khác nhau.</p>
 *
 * <p>KHÔNG có trường nào của hồ sơ sức khoẻ mèo hay {@code ph_value} của từng lần quét: p14
 * §14.3.2 mục 2 chặn mặc định, và đường xem có lý do là L4/L5 — hai endpoint riêng, chưa làm
 * trong gói này.</p>
 */
public record AdminUserDetailResponse(
        UUID userId,
        String emailMasked,
        String phoneMasked,
        String fullName,
        String status,
        String onboardingStatus,
        String locale,
        String timezone,
        boolean emailVerified,
        Instant emailVerifiedAt,
        Instant createdAt,
        Instant lastLoginAt,
        Instant lockedUntil,
        Instant deletionScheduledAt,
        boolean processingRestricted,
        boolean totpEnabled,
        int activeSessionCount,
        long catCount,
        long scanCount,
        String highestPackage,
        List<String> roles
) {

    public static AdminUserDetailResponse from(AdminUserService.AdminUserDetail detail) {
        AdminUserSummary summary = detail.summary();
        UserAccount account = detail.account();
        return new AdminUserDetailResponse(
                summary.userId(),
                PiiMask.email(summary.email()),
                detail.phoneMasked(),
                summary.fullName(),
                summary.status().name(),
                summary.onboardingStatus().name(),
                account.locale() == null ? null : account.locale().code(),
                account.timezone(),
                summary.emailVerifiedAt() != null,
                summary.emailVerifiedAt(),
                summary.createdAt(),
                summary.lastLoginAt(),
                summary.lockedUntil(),
                account.deletionScheduledAt(),
                account.isProcessingRestricted(),
                detail.totpEnabled(),
                detail.activeSessionCount(),
                summary.catCount(),
                summary.scanCount(),
                summary.highestPackage(),
                detail.roles().stream().map(UserRole::name).toList());
    }
}
