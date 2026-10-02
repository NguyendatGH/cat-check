package com.catcheck.shared.security;

import java.io.Serializable;
import java.util.Set;
import java.util.UUID;

/**
 * Principal tối giản đại diện người dùng đã xác thực, dùng xuyên module qua
 * {@code Authentication#getPrincipal()}. Module {@code identity} (M1+) là nơi tạo ra instance
 * này lúc xác thực thành công ({@code identity.application.AuthPrincipal} implements nó).
 *
 * <p><b>Bug thật đã sửa:</b> trước đây đây là một {@code record} và <b>không có nơi nào
 * {@code new SecurityPrincipal(...)}</b> — identity đặt {@code AuthPrincipal} (kiểu khác,
 * không họ hàng) vào {@code Authentication}. Spring resolve {@code @AuthenticationPrincipal
 * SecurityPrincipal} thành {@code null} khi kiểu principal không khớp, nên <b>cả 9 controller</b>
 * dùng kiểu này (cat, scan, credit, insight, export, privacy, policy, content, colorchart) đều
 * ném {@code NullPointerException} ⇒ 500 ở MỌI endpoint cần đăng nhập. Xác nhận thật bằng
 * {@code POST /api/v1/cats}: {@code Cannot invoke "SecurityPrincipal.userId()" because "user"
 * is null}. Chuyển thành interface để {@code AuthPrincipal} implement được mà vẫn giữ nguyên
 * các field riêng của identity ({@code authenticatedAt}, {@code reauthenticated}) và
 * {@code Principal#getName()} mà Spring Session JDBC cần.
 *
 * <p><b>Cảnh báo:</b> {@code email} là PII. KHÔNG log trực tiếp instance này (toString() của
 * bản cài đặt sẽ in cả email) — nếu cần log, chỉ log {@link #userId()}.</p>
 */
public interface SecurityPrincipal extends Serializable {

    UUID userId();

    String email();

    Set<String> roles();
}
