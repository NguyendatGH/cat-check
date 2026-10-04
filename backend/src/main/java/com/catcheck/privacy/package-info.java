/**
 * Quyền riêng tư: consent, chính sách, yêu cầu xoá/xuất dữ liệu cá nhân. Định nghĩa named
 * interface "spi" (com.catcheck.privacy.spi) mà các module khác implement để tham gia cơ chế
 * xoá dữ liệu xuyên module (xem ArchUnit R10 — mọi class implement privacy.spi.ErasureParticipant
 * phải nằm trong package con "application.privacy" của module đó).
 *
 * <p>Phụ thuộc {@code notification::api} (không phải cả module {@code notification}): rút
 * consent {@code HEALTH_REMINDER_PUSH} phải thu hồi {@code push_subscription} ngay lập tức
 * (p12 §12.3.9, p15 §15.3.5), và {@code NotificationGateway} là bề mặt duy nhất cho việc đó.
 * Trước W2-B danh sách ghi {@code "notification"} trống rỗng — module này chưa import type nào
 * của notification nên không ai phát hiện rằng tên đó trỏ vào named interface <i>unnamed</i>
 * (chỉ gồm package gốc), không phải {@code api}.</p>
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = { "shared", "notification::api", "audit::api" })
package com.catcheck.privacy;
