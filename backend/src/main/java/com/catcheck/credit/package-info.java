/**
 * Gói cước, hạn mức sử dụng, số dư credit của người dùng.
 *
 * <p>Phụ thuộc: {@code shared} (error/id/security/time) và {@code audit::api} (ghi audit_log khi
 * kích hoạt — p8 H1 cột {@code Aud}). Không phụ thuộc module khác: quan hệ với {@code app_user}
 * (identity) và {@code package_plan} (cat/content) qua UUID + cổng, không qua JPA association
 * (R6). {@code identity}/{@code notification}/{@code privacy::spi} đã bỏ khỏi danh sách vì module
 * không dùng — khai báo thừa chỉ làm tăng bề mặt phụ thuộc cần bảo vệ.</p>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = { "shared", "audit::api" })
package com.catcheck.credit;
