package com.catcheck.scan.api;

import com.catcheck.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

import java.net.URI;

/**
 * Mã lỗi nghiệp vụ của module {@code scan} — tên và HTTP status chép nguyên văn từ
 * {@code p8 §8.5.4} / {@code p6 §6.4.5} (Part 8 sở hữu danh mục mã lỗi, xem
 * {@code spec/04-index.md} §2). {@code CREDIT_INSUFFICIENT}/{@code WRITE_ACCESS_EXPIRED} KHÔNG
 * lặp lại ở đây — {@code scan} ném thẳng {@code credit.api.CreditErrorCode} tương ứng (một mã lỗi
 * một nguồn sự thật).
 *
 * <p><b>{@code CAT_ARCHIVED} cũng KHÔNG lặp lại ở đây</b> dù p8 §8.4.5 liệt nó vào danh mục lỗi
 * của {@code POST /scans}: hằng số này đã tồn tại nguyên xi ở
 * {@link com.catcheck.cat.api.CatErrorCode#CAT_ARCHIVED} ("409 — thao tác ghi trên mèo status =
 * ARCHIVED") do module {@code cat} định nghĩa trước (A3). Vì {@link ErrorCode#code()} phải DUY
 * NHẤT toàn hệ thống ({@code ErrorCodeRegistry.validate}, fail-fast lúc khởi động), định nghĩa
 * một hằng {@code CAT_ARCHIVED} thứ hai ở đây sẽ làm app không khởi động được ngay khi module
 * {@code cat} tự đăng ký {@code ErrorCode} của nó — {@code scan} tái dùng nguyên hằng của
 * {@code cat} thay vì tạo bản sao (xem {@code docs/handovers/A6.md} mục judgment call). Tương tự,
 * không định nghĩa mã "cat not found" riêng — theo đúng tinh thần p11 §11.7.4 (không phân biệt
 * "không tồn tại" với "không thuộc về bạn"), cat không tồn tại và cat thuộc người khác đều trả
 * {@link #CAT_NOT_OWNED}.</p>
 */
public enum ScanErrorCode implements ErrorCode {

    /** 400 — thiếu field, {@code scanRequestId} lệch header {@code Idempotency-Key}, ROI/quad hint sai dạng. */
    SCAN_METADATA_INVALID("scan-metadata-invalid", HttpStatus.BAD_REQUEST),
    /** 400 — cạnh dài ảnh {@code < 640px}. */
    IMAGE_TOO_SMALL("image-too-small", HttpStatus.BAD_REQUEST),
    /** 413 — ảnh {@code > 8MB} ở tầng nghiệp vụ (tầng biên rộng hơn, p11 §11.9.2). */
    IMAGE_TOO_LARGE("image-too-large", HttpStatus.PAYLOAD_TOO_LARGE),
    /** 415 — magic bytes không phải jpeg/png/webp. */
    IMAGE_FORMAT_UNSUPPORTED("image-format-unsupported", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    /** 400 — giải mã thất bại (file hỏng). */
    IMAGE_DECODE_FAILED("image-decode-failed", HttpStatus.BAD_REQUEST),
    /** 403 — {@code catId} không thuộc user đang đăng nhập, hoặc không tồn tại (không phân biệt). */
    CAT_NOT_OWNED("cat-not-owned", HttpStatus.FORBIDDEN),
    /** 409 — cùng {@code Idempotency-Key} đang xử lý dở (một instance, một request khác). */
    SCAN_IN_PROGRESS("scan-in-progress", HttpStatus.CONFLICT),
    /** 404 — không có scan nào ứng với {@code scanRequestId}, và cũng không đang xử lý. */
    SCAN_REQUEST_NOT_FOUND("scan-request-not-found", HttpStatus.NOT_FOUND),
    /** 404 — không tìm thấy, hoặc kết quả là {@code INCONCLUSIVE} (không có bản ghi hiển thị được, p8 §8.5.4). */
    SCAN_NOT_FOUND("scan-not-found", HttpStatus.NOT_FOUND),
    /** 404 — scan tồn tại nhưng không có {@code scan_analysis} hiện hành (không nên xảy ra ở trạng thái ANALYZED). */
    SCAN_ANALYSIS_NOT_FOUND("scan-analysis-not-found", HttpStatus.NOT_FOUND),
    /** 404 — {@code store_image = false}; {@code params.reason} trả {@code store_image_reason}. */
    SCAN_IMAGE_NOT_STORED("scan-image-not-stored", HttpStatus.NOT_FOUND),
    /** 410 — ảnh đã quá hạn lưu trữ 14 ngày. */
    SCAN_IMAGE_EXPIRED("scan-image-expired", HttpStatus.GONE),
    /** 409 — scan đã bị xoá mềm trước đó. */
    SCAN_ALREADY_DELETED("scan-already-deleted", HttpStatus.CONFLICT),
    /** 409 — quá 24 giờ kể từ {@code captured_at} (p6 §6.10.3). */
    SCAN_REASSIGN_WINDOW_CLOSED("scan-reassign-window-closed", HttpStatus.CONFLICT),
    /** 409 — đã đổi mèo đủ 3 lần. */
    SCAN_REASSIGN_LIMIT_REACHED("scan-reassign-limit-reached", HttpStatus.CONFLICT),
    /** 409 — scan đã xoá mềm/FAILED, không đổi mèo được. */
    SCAN_NOT_REASSIGNABLE("scan-not-reassignable", HttpStatus.CONFLICT),
    /** 409 — {@code toCatId} trùng {@code catId} hiện tại. */
    SCAN_REASSIGN_SAME_TARGET("scan-reassign-same-target", HttpStatus.CONFLICT),
    /** 409 — scan đã bị đánh dấu tranh chấp trước đó. */
    SCAN_ALREADY_DISPUTED("scan-already-disputed", HttpStatus.CONFLICT),
    /** 404 — gỡ đánh dấu tranh chấp khi chưa từng đánh dấu. */
    SCAN_DISPUTE_NOT_FOUND("scan-dispute-not-found", HttpStatus.NOT_FOUND),
    /** 422 — không có {@code color_chart} nào {@code ACTIVE} (seed placeholder thiếu). */
    SCAN_CHART_UNAVAILABLE("scan-chart-unavailable", HttpStatus.UNPROCESSABLE_ENTITY),
    /** 503 — OpenCV native không nạp được (xem {@code /actuator/health/vision}). */
    VISION_ENGINE_UNAVAILABLE("vision-engine-unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    /** 500 — exception trong pipeline SAU khi đã trừ credit → phải hoàn (p5 R7). */
    SCAN_PIPELINE_ERROR("scan-pipeline-error", HttpStatus.INTERNAL_SERVER_ERROR),
    /** 429 — hàng đợi {@code scanExecutor} đầy (R-B23, tách khỏi {@code RATE_LIMITED}). */
    SCAN_BUSY("scan-busy", HttpStatus.TOO_MANY_REQUESTS),
    /** 400 — {@code GET /scans} chỉ nhận sắp xếp {@code capturedAt,desc} khi dùng cursor. */
    SORT_NOT_SUPPORTED_WITH_CURSOR("sort-not-supported-with-cursor", HttpStatus.BAD_REQUEST);

    private static final String PROBLEM_BASE = "https://catcheck.vn/problems/";

    private final String slug;
    private final HttpStatus status;

    ScanErrorCode(String slug, HttpStatus status) {
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
