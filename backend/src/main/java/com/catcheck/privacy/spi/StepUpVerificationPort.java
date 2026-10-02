package com.catcheck.privacy.spi;

import java.util.UUID;

/**
 * Cổng kiểm tra step-up re-authentication cho thao tác nhạy cảm (p15 REQ-DSAR-04:
 * xuất/xoá dữ liệu BẮT BUỘC xác minh danh tính — user đang đăng nhập phải xác nhận lại).
 *
 * <p>Implement do module identity (A1) cung cấp: đọc trạng thái "đã reauth" mà
 * {@code POST /auth/reauth} ghi vào phiên (p11 §11.12.4, cửa sổ 300 giây). Không có
 * bean ở M1 ⇒ privacy phải <b>fail-closed</b> (trả
 * {@code DSAR_IDENTITY_VERIFICATION_REQUIRED}) chứ không âm thầm tin rằng request an
 * toàn — bỏ qua bước này là lỗ hổng chiếm dữ liệu kinh điển.</p>
 */
public interface StepUpVerificationPort {

    /** @param scope {@code "DATA_EXPORT"} hoặc {@code "ACCOUNT_ERASE"} — client ghi log, không ảnh hưởng luật */
    boolean isVerified(UUID userId, String scope);
}
