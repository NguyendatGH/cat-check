package com.catcheck.shared.security;

/**
 * Hằng tên role dùng chung toàn hệ thống.
 *
 * <p>TODO(M1+): xác nhận đầy đủ danh sách role nghiệp vụ với module {@code identity} (tra
 * p4/p7) — ở M0 chỉ khai báo tối thiểu để {@code shared.security} compile được và có chỗ tham
 * chiếu nhất quán (tránh mỗi module tự đặt chuỗi {@code "ROLE_..."} riêng).</p>
 */
public final class RoleConstants {

    public static final String ROLE_USER = "ROLE_USER";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";
    public static final String ROLE_SYSTEM = "ROLE_SYSTEM";

    private RoleConstants() {
    }
}
