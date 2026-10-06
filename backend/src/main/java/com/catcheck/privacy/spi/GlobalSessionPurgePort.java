package com.catcheck.privacy.spi;

/**
 * Cổng thực thi bước <b>R1 — Thu hồi TOÀN BỘ phiên</b> của sổ tay sự cố (p11 §11.13.4),
 * phục vụ endpoint L62 {@code POST /admin/security/sessions/purge-all}.
 *
 * <p><b>Vì sao cổng nằm ở {@code privacy.spi} mà bản cài đặt nằm ở module identity:</b>
 * nguồn sự thật về phiên là Spring Session JDBC ({@code SPRING_SESSION}) cộng với
 * {@code user_device_session} và {@code email_otp} — cả ba đều là bảng của identity
 * (p4 §4.3 nhóm A). Module {@code privacy} không được import type của identity
 * ({@code allowedDependencies} + R6), nên chiều phụ thuộc phải là identity → privacy::spi,
 * đúng khuôn {@link UserAccountPort} và {@link StepUpVerificationPort}.</p>
 *
 * <p><b>Vì sao không chỉ xoá {@code SPRING_SESSION}:</b> §11.13.4 liệt kê rõ những thứ
 * phải vô hiệu <i>trong cùng thao tác</i> — "nếu bỏ sót, kẻ tấn công vẫn còn đường vào".
 * Một mã OTP đang sống là một đường đăng nhập; một ticket đặt lại mật khẩu chưa dùng là
 * một đường đổi mật khẩu mà không cần mã mới. Vì vậy cổng này là <b>một</b> phương thức,
 * không phải ba phương thức để người gọi tự nhớ gọi đủ.</p>
 */
public interface GlobalSessionPurgePort {

    /**
     * Thu hồi mọi phiên của mọi tài khoản + vô hiệu mọi OTP/ticket đang mở. Idempotent:
     * gọi lần thứ hai trả về các số 0 chứ không lỗi.
     *
     * <p>Mục tiêu vận hành ≤ 5 phút (p8 L62, p11 §11.13.4) — cài đặt phải là thao tác theo
     * tập hợp (một câu lệnh cho mỗi bảng), không vòng lặp theo từng user.</p>
     */
    PurgeOutcome purgeEverySession();

    /**
     * @param httpSessionsDeleted   số dòng {@code SPRING_SESSION} bị xoá — số phiên thật
     *                              không còn dùng được nữa
     * @param deviceSessionsRevoked số dòng {@code user_device_session} vừa đánh dấu thu hồi
     *                              (bản sao để hiển thị màn "thiết bị đang đăng nhập")
     * @param otpInvalidated        số dòng {@code email_otp} còn hiệu lực vừa bị vô hiệu
     */
    record PurgeOutcome(int httpSessionsDeleted, int deviceSessionsRevoked, int otpInvalidated) {

        public static PurgeOutcome none() {
            return new PurgeOutcome(0, 0, 0);
        }
    }
}
