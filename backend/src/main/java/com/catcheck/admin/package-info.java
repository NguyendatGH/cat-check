/**
 * Quản trị hệ thống — phần <b>vận hành</b> của màn admin.
 *
 * <p>Hiện có: L64 {@code GET /admin/jobs/runs} và L66 {@code GET /admin/notifications/outbox}
 * (p8 §8.4.12 mục (f)), cùng K2 {@code GET /system/status}. Các nhóm admin khác nằm ở module sở
 * hữu nghiệp vụ, không ở đây: mã kích hoạt &amp; cấu hình gói (L19–L26) ở {@code credit},
 * người dùng (L1–L17) ở {@code identity}, bảng màu pH (L27–L39) ở {@code colorchart}, nội dung
 * (L40–L48) ở {@code content}. Lý do: cột {@code R:} của p8 ánh xạ sang nghiệp vụ, và một module
 * {@code admin} tổng hợp mọi thứ sẽ cần {@code allowedDependencies} tới gần như mọi module —
 * tức là xoá ranh giới Modulith thay vì tôn trọng nó.</p>
 *
 * <p>{@code audit::api} (không phải {@code audit} trần): {@code AuditLogService} nằm ở
 * {@code com.catcheck.audit.api}, còn một entry trần chỉ cấp quyền vào named-interface
 * "unnamed" — tức các type nằm TRỰC TIẾP ở gói gốc {@code com.catcheck.audit} (xem javadoc
 * {@code shared/package-info.java}).</p>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = { "shared", "audit::api" })
package com.catcheck.admin;
