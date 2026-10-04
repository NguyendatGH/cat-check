package com.catcheck.shared.security;

/**
 * Mức xác thực hai lớp ĐÃ đạt được trong phiên hiện tại — thuộc tính phiên {@code mfaLevel}
 * của p11 §11.12.1.
 *
 * <p>Nằm ở {@code shared} chứ không ở {@code identity} vì bộ gác {@code /api/v1/admin/**}
 * ({@link AdminAccessGate}) được cấu hình trong {@code shared.config.SecurityConfig} và phải
 * đọc được giá trị này từ {@link SecurityPrincipal} mà không import module {@code identity}.</p>
 *
 * <p><b>Không phải</b> "tài khoản có bật TOTP hay chưa" — đó là trạng thái đăng ký
 * ({@code user_mfa_totp.status}). Đây là "phiên này đã qua bước TOTP chưa", nên đăng nhập xong
 * mà chưa nhập mã 6 số thì vẫn là {@link #NONE}.</p>
 */
public enum MfaLevel {

    /** Chưa qua bước TOTP trong phiên này. Mọi endpoint {@code /api/v1/admin/**} bị từ chối. */
    NONE,

    /** Đã qua bước TOTP (A10/A11). p11 §11.12.1: hiệu lực 24 giờ, bằng absolute lifetime phiên admin. */
    TOTP
}
