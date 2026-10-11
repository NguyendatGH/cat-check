package com.catcheck.community.domain;

/**
 * Danh mục quy tắc giới hạn tần suất cho các luồng ghi dễ bị spam của module community:
 * {@code POST /community/posts}, {@code POST /community/posts/{id}/comments},
 * {@code POST /community/reports}.
 *
 * <p><b>Không có bản spec còn đọc được cho mấy ngưỡng này.</b> Thư mục {@code context/} (chứa
 * toàn bộ spec) đã bị xoá khỏi máy này trước khi việc rà soát này bắt đầu, nên không tra lại được
 * p8/p15 có chốt con số nào cho rate limit cộng đồng hay không. Trước khi có bản sửa này, module
 * community KHÔNG CÓ rate limiter nào — đăng bài/bình luận/report bị spam vô hạn chỉ bị chặn gián
 * tiếp bởi giới hạn độ dài trường (p4 §check constraint). Ba ngưỡng dưới đây là lựa chọn của người
 * rà soát, KHÔNG phải trích nguyên văn từ spec — cùng tình huống và cùng cách xử lý mà
 * {@code credit.domain.ActivationRateLimitRule} đã ghi nhận công khai khi gặp vấn đề giống hệt:</p>
 * <ul>
 *   <li>{@code POST_CREATE_BY_USER}: 5 bài/10 phút. Một người dùng thật hiếm khi đăng quá vài bài
 *       liên tiếp; đủ rộng cho thao tác thật, đủ hẹp để chặn spam làm trôi feed.</li>
 *   <li>{@code COMMENT_CREATE_BY_USER}: 20 bình luận/10 phút — rộng hơn đăng bài vì bình luận là
 *       tương tác nhẹ hơn (hỏi đáp qua lại nhanh trong một luồng là bình thường).</li>
 *   <li>{@code REPORT_CREATE_BY_USER}: 10 report/10 phút — report là tín hiệu kiểm duyệt; spam
 *       report (kể cả report "nhầm" hàng loạt) làm nghẽn hàng đợi của {@code CommunityModerationService}.</li>
 * </ul>
 *
 * <p>Nếu sau này có bản spec thật chốt lại các con số này, hãy đổi {@link #documentedRate} theo
 * đó và xoá ghi chú "chưa có spec" ở trên.</p>
 */
public enum CommunityRateLimitRule {

    /** {@code POST /community/posts}: 5 lần/10 phút theo {@code userId} đã đăng nhập. */
    POST_CREATE_BY_USER("5/10m"),

    /** {@code POST /community/posts/{id}/comments}: 20 lần/10 phút theo {@code userId}. */
    COMMENT_CREATE_BY_USER("20/10m"),

    /** {@code POST /community/reports}: 10 lần/10 phút theo {@code userId}. */
    REPORT_CREATE_BY_USER("10/10m");

    private final String documentedRate;

    CommunityRateLimitRule(String documentedRate) {
        this.documentedRate = documentedRate;
    }

    /** Chuỗi mức độ, dùng cho log và test. KHÔNG trích từ spec — xem javadoc của enum. */
    public String documentedRate() {
        return documentedRate;
    }

    /** Gốc của khoá đếm — tách theo quy tắc để các quy tắc không chia sẻ thùng. */
    public String bucketNamespace() {
        return name();
    }
}
