/**
 * Mua sắm (sản phẩm liên quan chăm sóc mèo).
 *
 * Catalogue sản phẩm, giỏ hàng và tạo đơn hàng.
 *
 * <p>{@code "audit::api"} — {@code AdminShopService} ghi nhật ký (tạo/sửa/đổi trạng thái sản
 * phẩm) qua {@code AuditLogService}, cùng cách module {@code identity}/{@code cat} khai (xem
 * named interface {@code api} của module {@code audit}, không phải entry trần {@code "audit"}).</p>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = { "shared", "audit::api" })
package com.catcheck.shop;
