package com.catcheck.scan.application;

/**
 * Che một đoạn văn bản tự do do người dùng nhập (tên mèo, ghi chú tranh chấp) trước khi hiện ở
 * màn quản trị — p11 §11.5.4 dòng "Hồ sơ mèo"/"Kết quả scan": {@code ADMIN_SUPPORT} và
 * {@code ADMIN_SUPER} đọc <b>"dữ liệu che"</b>, chỉ {@code DPO} đang xử lý DSAR đọc đầy đủ.
 *
 * <p><b>Vì sao không nằm ở {@code shared.security.PiiMask}:</b> {@code PiiMask} chép đúng hai
 * định dạng mà p15 REQ-RBAC-01 nêu ví dụ (email, điện thoại) cộng IP; văn bản tự do không có
 * định dạng nào trong spec, nên đây là một <i>quyết định của use case admin</i> chứ không phải
 * một quy ước toàn hệ thống — và {@code shared} nằm ngoài phạm vi sở hữu của gói việc này. Bản
 * song song ở {@code cat.application}; xem handoff H15.153.</p>
 *
 * <p>Giữ <b>đúng một</b> ký tự đầu: đủ để tổng đài đối chiếu với điều người dùng đọc qua điện
 * thoại ("bé tên bắt đầu bằng L phải không ạ"), không đủ để đọc lại nội dung.</p>
 */
public final class AdminFreeTextMask {

    private static final String MASKED = "***";

    private AdminFreeTextMask() {
    }

    /**
     * {@code "Luna"} ⇒ {@code "L***"}; chuỗi 1 ký tự hoặc rỗng ⇒ {@code "***"}; {@code null} ⇒
     * {@code null} (vắng dữ liệu và che dữ liệu là hai trạng thái khác nhau, client phải phân
     * biệt được).
     */
    public static String text(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.length() <= 1) {
            return MASKED;
        }
        return trimmed.charAt(0) + MASKED;
    }
}
