package com.catcheck.shared.security;

import jakarta.servlet.Filter;

/**
 * Bộ gác chung cho {@code /api/v1/admin/**} — p8 §8.4.12: <i>"Mọi endpoint {@code /admin/**}
 * còn chịu hai điều kiện chung: (1) phiên phải có {@code mfaLevel = TOTP}, thiếu ⇒
 * {@code 403 ADMIN_TOTP_REQUIRED}; chưa enroll ⇒ {@code 403 TOTP_SETUP_REQUIRED}"</i>.
 *
 * <p><b>Vì sao cần một interface riêng ở {@code shared} thay vì inject thẳng class cài đặt:</b>
 * chuỗi filter được dựng trong {@code shared.config.SecurityConfig}, nhưng việc biết một tài
 * khoản đã đăng ký TOTP hay chưa nằm ở {@code user_mfa_totp} — bảng của module
 * {@code identity}. Module {@code shared} khai {@code allowedDependencies = {}} nên không được
 * import {@code identity}; ngược lại {@code identity} được phép import {@code shared}. Đặt hợp
 * đồng ở đây và để {@code identity} cài đặt là cách duy nhất nối hai đầu mà không phá ranh giới
 * Modulith (và {@code ModularityTests} sẽ bắt ngay nếu làm khác).</p>
 *
 * <p>Cài đặt phải là một {@code @Component} duy nhất. Không có bean nào implement interface này
 * thì {@code SecurityConfig} <b>không</b> gắn gác — xem javadoc ở đó để biết vì sao lựa chọn
 * "không gắn" chứ không phải "chặn hết".</p>
 */
public interface AdminAccessGate extends Filter {
}
