/**
 * Gói cước, hạn mức sử dụng, số dư credit của người dùng.
 *
 * <p>Phụ thuộc: {@code shared} (error/id/security/time), {@code audit::api} (ghi audit_log khi
 * kích hoạt — p8 H1 cột {@code Aud}) và {@code notification::api}. Không phụ thuộc module khác:
 * quan hệ với {@code app_user} (identity) và {@code package_plan} (cat/content) qua UUID + cổng,
 * không qua JPA association (R6).</p>
 *
 * <p>Cạnh {@code notification::api} có từ W2-B (H15.46):
 * {@code infrastructure.notification.CreditExpiryNotificationAdapter} hiện thực
 * {@code application.spi.CreditExpiryNotificationPort} bằng {@code NotificationGateway}, để
 * {@code CreditExpiringReminderJob} đẩy {@code CREDIT_EXPIRING_T48H}/{@code _T6H} vào outbox
 * thay vì tự gửi (p12 §12.6.1 quy tắc 8). Chỉ chạm named interface {@code api}, không chạm
 * {@code notification.domain}/{@code .infrastructure}.</p>
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = { "shared", "audit::api", "notification::api" })
package com.catcheck.credit;
