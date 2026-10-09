package com.catcheck.ai.domain;

/**
 * Danh mục quy tắc giới hạn tần suất cho {@code POST /api/v1/ai/chat} (nhiệm vụ wiring A2-A4,
 * {@code docs/cat-rag-credit-pipeline.md} mục A4-(4): "Rate limit theo user (vd 20 câu/giờ)").
 *
 * <p><b>CHƯA có mục p11 tương ứng cho rate limit chat AI.</b> Thư mục {@code context/} (chứa toàn
 * bộ spec, kể cả p11) đã bị xoá khỏi máy này trước khi nhiệm vụ này bắt đầu nên không tra lại được
 * liệu bảng gốc có từng chốt con số nào cho {@code POST /ai/chat} hay không — tài liệu duy nhất
 * còn đọc được ({@code docs/cat-rag-credit-pipeline.md} mục A4-(4)) chỉ gợi ý <i>"vd 20 câu/giờ"</i>,
 * không phải một con số đã chốt. Ngưỡng dưới đây là lựa chọn của người viết phần việc này,
 * KHÔNG phải trích nguyên văn từ spec:</p>
 * <ul>
 *   <li>{@code AI_CHAT_BY_USER}: 20 lần/giờ theo {@code userId} đã đăng nhập. Mỗi câu hỏi có thể
 *       gọi một nhà cung cấp LLM trả phí khi bật ({@code AiProviderPort}) — khác hẳn một thao tác
 *       đọc thường, nên cần giới hạn dù endpoint không sửa dữ liệu nghiệp vụ. 20/giờ đủ rộng cho
 *       một phiên hỏi-đáp thật (trung bình chưa tới 1 câu/3 phút) nhưng đủ hẹp để chặn vòng lặp
 *       gọi tự động/lạm dụng chi phí provider.</li>
 * </ul>
 *
 * <p><b>Chỉ MỘT lớp theo user, KHÔNG có lớp phụ theo IP</b> như
 * {@code credit.domain.ActivationRateLimitRule} hay
 * {@code shared.application.spi.PublicIngressRateLimiter}: {@code POST /ai/chat} luôn yêu cầu
 * phiên đăng nhập hợp lệ ({@code AiController#chat} nhận {@code @CurrentUser SecurityPrincipal}),
 * nên luôn có {@code userId} làm khoá đếm — không có tình huống "chưa xác định được nguồn gọi"
 * cần một lớp IP dự phòng như hai endpoint CÔNG KHAI (không đăng nhập) mà
 * {@code PublicIngressRateLimiter} đang bảo vệ.</p>
 *
 * <p>Nếu sau này có bản spec thật chốt lại con số này (p11 khôi phục hoặc bổ sung), hãy đổi
 * {@link #documentedRate()} theo đó và xoá ghi chú "chưa có spec" ở trên.</p>
 */
public enum AiChatRateLimitRule {

    /** {@code POST /ai/chat}: 20 lần/giờ theo {@code userId} đã đăng nhập. */
    AI_CHAT_BY_USER("20/1h");

    private final String documentedRate;

    AiChatRateLimitRule(String documentedRate) {
        this.documentedRate = documentedRate;
    }

    /** Chuỗi mức độ, dùng cho log và test. KHÔNG trích từ spec — xem javadoc của enum. */
    public String documentedRate() {
        return documentedRate;
    }

    /** Gốc của khoá đếm — tách theo quy tắc để nhiều quy tắc (nếu thêm sau này) không chia sẻ thùng. */
    public String bucketNamespace() {
        return name();
    }
}
