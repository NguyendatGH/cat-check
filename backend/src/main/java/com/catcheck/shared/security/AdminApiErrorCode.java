package com.catcheck.shared.security;

import com.catcheck.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

import java.net.URI;

/**
 * Bốn mã lỗi mà <b>mọi</b> endpoint {@code /api/v1/admin/**} dùng chung, bất kể module nào sở
 * hữu nghiệp vụ — p8 §8.2.4 + §8.3.2 (ký hiệu {@code Rsn}, {@code If-Match}).
 *
 * <p><b>Vì sao ở {@code shared} chứ không lặp lại trong từng module:</b> ba module cùng phục vụ
 * màn quản trị ({@code credit} cho mã kích hoạt/gói, {@code identity} cho người dùng,
 * {@code admin} cho job/outbox) đều cần đúng bốn mã này. Lặp lại enum ở cả ba nơi nghĩa là ba
 * bản {@code HttpStatus} có thể trôi khỏi nhau trong khi client chỉ đọc một trường
 * {@code errorCode}.</p>
 *
 * <p><b>Chỉ {@link #ACCESS_DENIED} được đăng ký bean {@code ErrorCode}</b>
 * ({@code SharedErrorCodeConfiguration}). Ba mã còn lại đã tồn tại dưới dạng hằng trong
 * {@code ColorChartErrorCode}/{@code CatErrorCode}/{@code ContentErrorCode} (những enum đó cố ý
 * không đăng ký bean — xem javadoc {@code CatErrorCodeConfiguration}), nên đăng ký thêm ở đây
 * sẽ làm {@code ErrorCodeRegistry} báo trùng và app không khởi động được ngay khi một trong các
 * module đó đổi ý. Không đăng ký KHÔNG ảnh hưởng hành vi: {@code GlobalExceptionHandler} đọc
 * {@code ErrorCode} trực tiếp từ exception, registry chỉ làm nhiệm vụ chống trùng lúc khởi
 * động.</p>
 */
public enum AdminApiErrorCode implements ErrorCode {

    /**
     * 403 — phiên hợp lệ, đã qua TOTP, nhưng vai trò không nằm trong cột {@code R:} của endpoint
     * (p8 §8.3.1 bước 4a, ma trận p11 §11.5.4). Tham số: danh sách vai trò được phép.
     */
    ACCESS_DENIED("access-denied", HttpStatus.FORBIDDEN),

    /**
     * 400 — hành động admin có ký hiệu {@code Rsn} mà thiếu {@code reason} hoặc {@code reason}
     * ngắn hơn 10 ký tự. p15 REQ-AUD-03: <i>"request thiếu {@code reason} bị từ chối 400"</i> —
     * là ràng buộc API, không phải nhắc nhở UI. Tham số: số ký tự tối thiểu.
     */
    REASON_REQUIRED("reason-required", HttpStatus.BAD_REQUEST),

    /** 428 — endpoint ghi cấu hình thiếu header {@code If-Match} (p8 §8.1.11). */
    PRECONDITION_REQUIRED("precondition-required", HttpStatus.PRECONDITION_REQUIRED),

    /** 412 — {@code If-Match} không khớp ETag hiện tại: ai đó vừa sửa trước mình. */
    RESOURCE_MODIFIED("resource-modified", HttpStatus.PRECONDITION_FAILED);

    private static final String PROBLEM_BASE = "https://catcheck.vn/problems/";

    private final String slug;
    private final HttpStatus status;

    AdminApiErrorCode(String slug, HttpStatus status) {
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
