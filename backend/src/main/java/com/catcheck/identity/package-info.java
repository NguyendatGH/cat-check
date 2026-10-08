/**
 * Tài khoản người dùng, đăng nhập, MFA (user_mfa_totp), phiên đăng nhập.
 *
 * <p>Danh sách phụ thuộc (mỗi module khác chỉ được import qua <b>named interface</b>,
 * không qua package gốc — R6):</p>
 * <ul>
 *   <li>{@code shared} — error/i18n/security/time/id của M0.</li>
 *   <li>{@code audit::api} — ghi nhat ky dung ({@code AuditLogService}).</li>
 *   <li>{@code notification::api} — gửi email OTP ({@code EmailSender}).</li>
 *   <li>{@code privacy::spi} — khi bên ngoài hủy tài khoản/ẩn danh.</li>
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {
        "shared",
        "audit::api",
        "media::api",
        "notification::api",
        "privacy::spi" })
package com.catcheck.identity;
