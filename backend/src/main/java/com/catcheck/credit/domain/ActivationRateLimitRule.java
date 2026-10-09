package com.catcheck.credit.domain;

/**
 * Danh mục quy tắc giới hạn tần suất cho {@code POST /activations} (G3,
 * {@code docs/cat-rag-credit-pipeline.md} B2).
 *
 * <p><b>CHƯA có mục p11 tương ứng cho rate limit kích hoạt mã credit.</b> Thư mục
 * {@code context/} (chứa toàn bộ spec, kể cả p11) đã bị xoá khỏi máy này trước khi nhiệm vụ này
 * bắt đầu nên không tra lại được liệu bảng gốc có từng chốt con số nào cho
 * {@code POST /activations} hay không — tài liệu duy nhất còn đọc được
 * ({@code docs/cat-rag-credit-pipeline.md} mục G3) chỉ gợi ý <i>"vd 5 lần/15 phút, 20/ngày"</i>,
 * không phải một con số đã chốt. Hai ngưỡng dưới đây là lựa chọn của người viết phần việc này khi
 * làm G3, KHÔNG phải trích nguyên văn từ spec:</p>
 * <ul>
 *   <li>{@code ACTIVATION_BY_USER}: 5 lần/15 phút. Một người dùng thật gõ nhầm mã (nhầm O/0, thiếu
 *       dấu gạch ngang, quên một ký tự) hiếm khi sai quá 3-4 lần liên tiếp; 5 lần đủ rộng cho thao
 *       tác thật nhưng đủ hẹp để chặn dò vét bằng tay. Mượn đúng hình dạng {@code MFA_VERIFY}
 *       (5/5 phút) của {@code identity.domain.RateLimitRule}, nới cửa sổ lên 15 phút vì kích hoạt
 *       mã không có cơ chế khoá 2 lớp như TOTP.</li>
 *   <li>{@code ACTIVATION_BY_IP}: 20 lần/giờ — LỚP PHỤ, rộng hơn hẳn ngưỡng theo user vì CGNAT ở
 *       Việt Nam khiến hàng nghìn thuê bao dùng chung một IPv4 (cùng lý do
 *       {@code identity.domain.RateLimitRule} đã ghi ở §11.7.1). Mục đích của lớp này là chặn một
 *       nguồn duy nhất dò hàng loạt userId/mã khác nhau, không phải chặn người dùng thật.</li>
 * </ul>
 *
 * <p>Nếu sau này có bản spec thật chốt lại hai con số này (p11 §11.7.2 khôi phục hoặc bổ sung),
 * hãy đổi {@link #documentedRate} theo đó và xoá ghi chú "chưa có spec" ở trên.</p>
 */
public enum ActivationRateLimitRule {

    /** G3 — {@code POST /activations}: 5 lần/15 phút theo {@code userId} đã đăng nhập. */
    ACTIVATION_BY_USER("5/15m", KeyKind.USER_ID),

    /** G3 — {@code POST /activations}: 20 lần/giờ theo IP. Lớp phụ, xem javadoc của enum. */
    ACTIVATION_BY_IP("20/1h", KeyKind.IP);

    /** Loại khoá dùng để đếm. */
    public enum KeyKind {
        USER_ID,
        IP
    }

    private final String documentedRate;
    private final KeyKind keyKind;

    ActivationRateLimitRule(String documentedRate, KeyKind keyKind) {
        this.documentedRate = documentedRate;
        this.keyKind = keyKind;
    }

    /** Chuỗi mức độ, dùng cho log và test. KHÔNG trích từ spec — xem javadoc của enum. */
    public String documentedRate() {
        return documentedRate;
    }

    public KeyKind keyKind() {
        return keyKind;
    }

    /** Gốc của khoá đếm — tách theo quy tắc để hai quy tắc không chia sẻ thùng. */
    public String bucketNamespace() {
        return name();
    }
}
