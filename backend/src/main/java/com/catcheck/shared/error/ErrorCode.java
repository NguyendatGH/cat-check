package com.catcheck.shared.error;

import org.springframework.http.HttpStatus;

import java.net.URI;

/**
 * Hợp đồng cho mã lỗi nghiệp vụ của từng module. Mỗi enum mã lỗi của module (ví dụ
 * {@code scan.application.ScanErrorCode}, sẽ được thêm từ M1+) implement interface này.
 *
 * <p>Ở M0 chưa có module nghiệp vụ nào định nghĩa ErrorCode thật — interface này chỉ là hợp
 * đồng kiến trúc để {@link ErrorCodeRegistry} và {@link GlobalExceptionHandler} có thể xử lý
 * đồng nhất mọi loại lỗi nghiệp vụ về sau.</p>
 */
public interface ErrorCode {

    /** Mã lỗi duy nhất trong toàn hệ thống, ví dụ {@code "SCAN-001"}. */
    String code();

    /** HTTP status tương ứng, dùng làm status của {@link org.springframework.http.ProblemDetail}. */
    HttpStatus status();

    /** URI định danh loại lỗi (trường {@code type} của RFC 9457 ProblemDetail). */
    URI typeUri();
}
