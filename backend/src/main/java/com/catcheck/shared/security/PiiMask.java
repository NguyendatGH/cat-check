package com.catcheck.shared.security;

/**
 * Che PII cho màn quản trị — p15 REQ-RBAC-01: <i>"Mặc định mask mọi PII trong admin panel
 * ({@code ngu***@gmail.com}, {@code 090****567}). Bỏ mask là hành động tường minh, có lý do, ghi
 * audit, và tự khôi phục mask sau 15 phút."</i>
 *
 * <p>Hai định dạng dưới đây <b>chép đúng từ hai ví dụ của p15</b>, không tự nghĩ: giữ 3 ký tự
 * đầu của phần cục bộ email, và giữ 3 số đầu + 3 số cuối của số điện thoại. Hai ví dụ đó cũng
 * là thứ test REQ-RBAC-01 sẽ neo vào.</p>
 *
 * <p><b>Mask là hiển thị, không phải bảo mật.</b> Nó chống nhìn-qua-vai và chống rò qua ảnh chụp
 * màn hình/bản ghi phiên hỗ trợ; nó không chống một admin có chủ đích, vì người đó bấm nút
 * unmask (L3) và việc đó ghi {@code audit_log} — chính bản ghi đó mới là biện pháp.</p>
 *
 * <p><b>Nằm ở {@code shared}</b> vì ba module cùng cần đúng hai định dạng này: {@code identity}
 * (L1/L2 — email, phone, IP của phiên), {@code admin} (L66 — địa chỉ người nhận trong
 * {@code email_outbox}), và bất kỳ màn admin nào thêm sau. Lặp lại hàm che ở từng module là cách
 * chắc chắn nhất để hai màn hiển thị hai mức che khác nhau cho cùng một người. Thuần Java, không
 * annotation framework.</p>
 */
public final class PiiMask {

    /** Số ký tự đầu của phần cục bộ email được giữ lại — {@code ngu***@gmail.com}. */
    private static final int EMAIL_LOCAL_VISIBLE = 3;

    /** Số chữ số đầu/cuối của số điện thoại được giữ lại — {@code 090****567}. */
    private static final int PHONE_EDGE_VISIBLE = 3;

    private static final String FULLY_MASKED = "***";

    private PiiMask() {
    }

    /**
     * {@code nguyenvana@gmail.com} ⇒ {@code ngu***@gmail.com}.
     *
     * <p>Tên miền giữ nguyên, đúng ví dụ của p15: nó không định danh một người và tổng đài cần
     * nó để phân biệt tài khoản doanh nghiệp với tài khoản cá nhân.</p>
     *
     * <p>Phần cục bộ ngắn hơn hoặc bằng {@value #EMAIL_LOCAL_VISIBLE} ký tự thì che <b>toàn
     * bộ</b> thay vì giữ lại gần hết: {@code ab@x.vn} mà hiện {@code ab***@x.vn} thì mask không
     * che được gì cả.</p>
     */
    public static String email(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int at = email.lastIndexOf('@');
        if (at <= 0 || at == email.length() - 1) {
            return FULLY_MASKED;
        }
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        String visible = local.length() <= EMAIL_LOCAL_VISIBLE
                ? "" : local.substring(0, EMAIL_LOCAL_VISIBLE);
        return visible + FULLY_MASKED + "@" + domain;
    }

    /**
     * {@code 0901234567} ⇒ {@code 090****567}.
     *
     * <p>Số ngắn hơn {@code 2 ×} {@value #PHONE_EDGE_VISIBLE} {@code + 1} chữ số thì che toàn
     * bộ — giữ 3 đầu và 3 cuối của một số 7 chữ số là hiện 6/7.</p>
     */
    public static String phone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String digits = phone.strip();
        if (digits.length() <= PHONE_EDGE_VISIBLE * 2) {
            return FULLY_MASKED;
        }
        int hidden = digits.length() - PHONE_EDGE_VISIBLE * 2;
        return digits.substring(0, PHONE_EDGE_VISIBLE)
                + "*".repeat(hidden)
                + digits.substring(digits.length() - PHONE_EDGE_VISIBLE);
    }

    /**
     * {@code 203.0.113.42} ⇒ {@code 203.0.113.*}; IPv6 ⇒ che từ nhóm thứ tư.
     *
     * <p>p15 không nêu IP trong REQ-RBAC-01, nhưng IP là dữ liệu cá nhân theo Nghị định 13 và
     * màn phiên đăng nhập (L11) hiện nó cho một người <b>khác</b> chủ sở hữu — nên che theo cùng
     * nguyên tắc. Giữ phần mạng vì đó là thứ tổng đài cần ("đăng nhập từ nhà hay từ nơi khác").
     * Cùng cách {@code AuthController.maskIp} đang làm cho màn thiết bị của chính người dùng.</p>
     */
    public static String ipAddress(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        if (ip.indexOf(':') >= 0) {
            String[] groups = ip.split(":");
            if (groups.length <= 3) {
                return FULLY_MASKED;
            }
            return String.join(":", groups[0], groups[1], groups[2]) + ":*";
        }
        int lastDot = ip.lastIndexOf('.');
        return lastDot > 0 ? ip.substring(0, lastDot) + ".*" : FULLY_MASKED;
    }
}
