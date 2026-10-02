package com.catcheck.privacy.spi;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Ảnh chụp tài khoản người dùng lấy qua {@link com.catcheck.privacy.spi.UserAccountPort}.
 *
 * <p>Module privacy <b>không</b> import type của module identity (R6 + ràng buộc của ORCHESTRATOR):
 * mọi tham chiếu chéo module đều qua UUID + cổng này. Trạng thái dùng chuỗi nguyên văn
 * khớp enum {@code app_user.status} của p4 §4.4.2 để privacy không phụ thuộc vào class
 * enum của identity.</p>
 *
 * <p>Nằm trong named interface {@code privacy.spi} (không phải {@code privacy.domain}) vì đây là
 * kiểu trả về của {@link UserAccountPort#snapshot(UUID)} — module implement cổng này (identity)
 * phải thấy được kiểu này, và Spring Modulith chỉ cho thấy các type nằm trong named interface đã
 * khai báo (xem {@code privacy/spi/package-info.java}), không phải mọi type public trong module.</p>
 *
 * @param userId                  id tài khoản
 * @param email                   email hiện tại — PII, chỉ dùng server-side (contact_email của DSAR),
 *                                KHÔNG log, KHÔNG trả trong response API công khai
 * @param status                  PENDING_VERIFICATION/ACTIVE/LOCKED/RESTRICTED/DELETION_REQUESTED/ANONYMIZED
 * @param processingRestrictedAt  mốc bật hạn chế xử lý (p15 §15.4.7), null nếu không hạn chế
 * @param deletionScheduledAt    mốc dự kiến xoá cứng (D+7), null nếu không trong ân hạn
 * @param anonymizedAt            mốc đã ẩn danh hoá xong (p4 §4.1.4)
 * @param pseudonymId             id user tombstone thay user_id các bảng phải giữ (p4 B2)
 * @param roles                   vai trò nguyên văn khớp {@code user_role.role} (p4 §4.4.2), kể cả "DPO"
 */
public record UserAccountSnapshot(
        UUID userId,
        String email,
        String status,
        Instant processingRestrictedAt,
        Instant deletionScheduledAt,
        Instant anonymizedAt,
        UUID pseudonymId,
        Set<String> roles
) {

    public UserAccountSnapshot {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public boolean isProcessingRestricted() {
        return processingRestrictedAt != null;
    }

    public boolean isDeletionRequested() {
        return deletionScheduledAt != null;
    }

    public boolean isAnonymized() {
        return anonymizedAt != null;
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
