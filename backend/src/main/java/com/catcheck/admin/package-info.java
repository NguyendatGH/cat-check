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
 *
 * <p><b>{@code notification::api} thêm ở W5-D, và đây là một ngoại lệ có căn cứ chứ không phải
 * nới lỏng.</b> Hai endpoint vận hành của p8 §8.4.12 mục (f) — L67
 * {@code POST /admin/notifications/outbox/{id}/resend} và L72
 * {@code POST /admin/system/broadcast} — là <b>hành động</b> trên hai bảng outbox và trên
 * {@code notification}, tức là trên dữ liệu mà module {@code notification} sở hữu (p4 F2/F5)
 * cùng toàn bộ luật retry/backoff/dedupe/consent của p12 §12.8. Hai lựa chọn còn lại đều tệ
 * hơn: (a) để {@code admin} tự viết {@code UPDATE} thì bỏ qua hết các luật đó — chính điều mà
 * handoff H15.106 đã ghi là không được làm; (b) dựng hai endpoint admin bên trong module
 * {@code notification} thì phá nguyên tắc "L6x vận hành thuộc {@code admin}" mà javadoc trên
 * vừa nêu, và sẽ nhân bản {@code AdminGuard} + ghi audit sang một module nữa. Phụ thuộc chỉ
 * tới <b>named interface</b> {@code api} (ba interface: {@code NotificationGateway},
 * {@code OutboxAdminGateway}, {@code SystemBroadcastGateway}) nên không có type nội bộ nào của
 * {@code notification} lọt vào {@code admin}, và {@code ModularityTests} canh đúng điều đó.</p>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = { "shared", "audit::api", "notification::api" })
package com.catcheck.admin;
