package com.catcheck.colorchart.api;

import com.catcheck.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

import java.net.URI;

/**
 * Mã lỗi nghiệp vụ của module colorchart.
 *
 * <p>Tên mã và HTTP status chốt theo p8 §8.2.4(j) — Part 8 sở hữu danh mục mã lỗi, nên ở đây
 * chép NGUYÊN VĂN, không tự đặt lại tên. {@link #code()} đồng thời là khoá tra message trong
 * {@code messages/colorchart_vi.properties} / {@code colorchart_en.properties}.
 *
 * <p>Chưa đăng ký vào {@code ErrorCodeRegistry}: registry nhận {@code List<ErrorCode>} qua
 * constructor, nên nếu mỗi module cùng expose một {@code @Bean List<ErrorCode>} thì Spring sẽ
 * không còn bean định danh duy nhất. Cần W3 gộp lại (xem docs/handovers/A5.md).
 */
public enum ColorChartErrorCode implements ErrorCode {

    /** 404 — bảng màu không tồn tại. Tham số: {@code chartId}. */
    COLOR_CHART_NOT_FOUND("color-chart-not-found", HttpStatus.NOT_FOUND),

    /** 409 — sửa bảng màu đang {@code ACTIVE} (p8 L30). Tham số: {@code chartId}, {@code status}. */
    COLOR_CHART_IN_USE("color-chart-in-use", HttpStatus.CONFLICT),

    /** 422 — publish bảng màu thiếu điểm hoặc thiếu dải phân loại (p8 L32). Tham số: {@code missing}. */
    COLOR_CHART_INCOMPLETE("color-chart-incomplete", HttpStatus.UNPROCESSABLE_ENTITY),

    /** 409 — {@code (code, version)} đã tồn tại. Tham số: {@code code}, {@code version}. */
    COLOR_CHART_CODE_TAKEN("color-chart-code-taken", HttpStatus.CONFLICT),

    /** 404 — dải phân loại không tồn tại. Tham số: {@code code}. */
    PH_BAND_NOT_FOUND("ph-band-not-found", HttpStatus.NOT_FOUND),

    /** 409 — dải phân loại chồng lấn trên trục pH (I9). Tham số: {@code code}, {@code otherCode}. */
    PH_BAND_OVERLAP("ph-band-overlap", HttpStatus.CONFLICT),

    /** 400 — thiếu {@code reason} dài ≥ 10 ký tự ở endpoint bắt buộc {@code Rsn} (p8 §8.3.2). Tham số: {@code minLength}. */
    REASON_REQUIRED("reason-required", HttpStatus.BAD_REQUEST),

    /** 403 — thiếu vai trò quản trị (p8 §8.3.2 {@code R:ADMIN_SUPER,ADMIN_CATALOG}). Tham số: {@code required}. */
    ADMIN_ROLE_REQUIRED("admin-role-required", HttpStatus.FORBIDDEN),

    /** 400 — tham số không hợp lệ (p8 §8.2.4(a)). */
    VALIDATION_FAILED("validation-failed", HttpStatus.BAD_REQUEST),

    /** 428 — thiếu {@code If-Match} ở endpoint cấu hình (p8 §8.1.11). */
    PRECONDITION_REQUIRED("precondition-required", HttpStatus.PRECONDITION_REQUIRED),

    /** 412 — {@code If-Match} không khớp ETag (p8 §8.1.11). Tham số: {@code currentEtag}. */
    RESOURCE_MODIFIED("resource-modified", HttpStatus.PRECONDITION_FAILED);

    private static final String PROBLEM_BASE = "https://catcheck.vn/problems/";

    private final String slug;
    private final HttpStatus status;

    ColorChartErrorCode(String slug, HttpStatus status) {
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
