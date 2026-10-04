package com.catcheck.notification.domain;

/**
 * {@code push_subscription.revoke_reason} (p4 F3, p12 §12.3.2).
 *
 * <p>p12 bắt buộc ghi ĐÚNG lý do: nó phân biệt "token chết do kỹ thuật"
 * ({@code FCM_*}, tính vào {@code catcheck.push_token.revoked_rate}) với "user rút consent"
 * ({@code CONSENT_WITHDRAWN}, là bằng chứng tuân thủ p15 §15.3.5 — KHÔNG được tính vào tỉ lệ
 * lỗi kỹ thuật).</p>
 */
public enum PushRevokeReason {
    /** FCM trả {@code UNREGISTERED}. */
    FCM_UNREGISTERED,
    /** FCM trả {@code INVALID_ARGUMENT}. */
    FCM_INVALID,
    /** User tự gỡ thiết bị (G11), hoặc thiết bị đổi chủ (cùng FID, user khác). */
    USER_DISABLED,
    /** Rút consent {@code HEALTH_REMINDER_PUSH} (p15 §15.3.5). */
    CONSENT_WITHDRAWN,
    /** Xoá tài khoản (p15 §15.4.6, D+0). */
    ACCOUNT_DELETION,
    /** Không hoạt động quá 180 ngày, hoặc {@code failure_count > 10} (p12 §12.3.9). */
    STALE
}
