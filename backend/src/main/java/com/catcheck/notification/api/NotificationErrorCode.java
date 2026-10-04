package com.catcheck.notification.api;

import com.catcheck.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

import java.net.URI;

/** Mã lỗi nhóm G — p8 §8.2.4(g) và §8.2.4(a). Tên/status lấy nguyên văn, không tự đặt. */
public enum NotificationErrorCode implements ErrorCode {

    /** 404 — không tồn tại HOẶC không thuộc user (p8 §8.2.5: tài nguyên của người khác trả 404). */
    NOTIFICATION_NOT_FOUND("notification/not-found", HttpStatus.NOT_FOUND),

    /** 400 — thiếu cả {@code fid} lẫn {@code legacyToken} (p4 F3 {@code ck_push_identifier}). */
    PUSH_SUBSCRIPTION_INVALID("push/subscription-invalid", HttpStatus.BAD_REQUEST),

    /** 409 — vượt trần 10 thiết bị/user. Tham số: {@code max}. */
    PUSH_SUBSCRIPTION_LIMIT("push/subscription-limit", HttpStatus.CONFLICT),

    /**
     * 400 — {@code templateCode} không có trong registry {@code NotificationTemplate}.
     *
     * <p>p8 §8.2.4 KHÔNG có mã này: không endpoint nào cho client chọn template, nên đây là lỗi
     * lập trình nội bộ chứ không phải lỗi hợp đồng API. Giữ ở đây để thông điệp có mã thay vì
     * {@code 500} trắng, và vì thế cũng KHÔNG nằm trong danh mục mã lỗi của p8.</p>
     */
    NOTIFICATION_TEMPLATE_UNKNOWN("notification/template-unknown", HttpStatus.BAD_REQUEST),

    /**
     * 403 — bật push khi chưa đồng ý {@code HEALTH_REMINDER_PUSH} (p8 §8.4.7 G10 cột Auth
     * {@code U + C:HEALTH_REMINDER_PUSH}).
     *
     * <p>TRÙNG {@code code()} với {@code PrivacyErrorCode.CONSENT_REQUIRED}, và
     * {@code PrivacyErrorCodeConfiguration} ĐÃ đăng ký bean. Theo quy ước đã ghi ở
     * {@code ReminderErrorCode}: giữ hằng để ném được, KHÔNG đăng ký bean lần nữa — đăng ký
     * trùng làm {@code ErrorCodeRegistry.validate} ném {@code IllegalStateException} lúc khởi
     * động.</p>
     */
    CONSENT_REQUIRED("consent-required", HttpStatus.FORBIDDEN),

    /** 400 — cursor sai định dạng/hết hiệu lực (p8 §8.1.4). Đã đăng ký ở privacy; chỉ ném. */
    PAGINATION_CURSOR_INVALID("pagination-cursor-invalid", HttpStatus.BAD_REQUEST),

    /** 400 — {@code limit} vượt 100; p8 §8.1.4 cấm âm thầm kẹp xuống. Đã đăng ký ở nơi khác; chỉ ném. */
    VALIDATION_FAILED("validation-failed", HttpStatus.BAD_REQUEST);

    private static final String PROBLEM_BASE = "https://catcheck.vn/problems/";

    private final String slug;
    private final HttpStatus status;

    NotificationErrorCode(String slug, HttpStatus status) {
        this.slug = slug;
        this.status = status;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public URI typeUri() {
        return URI.create(PROBLEM_BASE + slug);
    }
}
