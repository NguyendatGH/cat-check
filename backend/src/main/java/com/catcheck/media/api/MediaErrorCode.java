package com.catcheck.media.api;

import com.catcheck.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

import java.net.URI;

/**
 * Mã lỗi nghiệp vụ của module media. p8 §8.2.4 chưa mục nào dành riêng cho media — những lỗi ở
 * đây đều là lỗi hạ tầng lưu trữ và map sang nhóm "Chung" của p8.
 *
 * <p>Chưa đăng ký vào {@code ErrorCodeRegistry}: registry nhận {@code List<ErrorCode>} qua
 * constructor, nên nếu mỗi module cùng expose một {@code @Bean List<ErrorCode>} thì Spring sẽ
 * không còn bean nào định danh duy nhất và context sẽ không khởi động. Xem
 * {@code docs/handovers/A3.md} mục "Việc còn lại của W3".</p>
 */
public enum MediaErrorCode implements ErrorCode {

    /** 415 — MIME type không thuộc danh sách cho phép (jpeg/png/webp — p6 §6.3.5). Tham số: {@code contentType}. */
    STORAGE_UNSUPPORTED_TYPE("storage-unsupported-type", HttpStatus.UNSUPPORTED_MEDIA_TYPE),

    /** 413 — ảnh vượt giới hạn kích thước của module sở hữu. Tham số: {@code maxBytes}, {@code actualBytes}. */
    STORAGE_PAYLOAD_TOO_LARGE("storage-payload-too-large", HttpStatus.PAYLOAD_TOO_LARGE),

    /** 404 — khoá không tồn tại trong storage. Tham số: {@code key}. */
    STORAGE_KEY_NOT_FOUND("storage-key-not-found", HttpStatus.NOT_FOUND),

    /** 500 — ghi/xoá tệp lỗi ở tầng hạ tầng (quyền đĩa, ổ đầy, symlink loop). */
    STORAGE_IO_ERROR("storage-io-error", HttpStatus.INTERNAL_SERVER_ERROR);

    private static final String PROBLEM_BASE = "https://catcheck.vn/problems/";

    private final String slug;
    private final HttpStatus status;

    MediaErrorCode(String slug, HttpStatus status) {
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
