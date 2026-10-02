package com.catcheck.shared.error;

/**
 * Lỗi tạm thời, có thể retry (ví dụ: pipeline nhận diện màu OpenCV timeout). Phân biệt với
 * {@link ExternalServiceException} ở chỗ lỗi này có thể đến từ chính nội bộ hệ thống (không chỉ
 * dịch vụ ngoài), thường HTTP 503.
 */
public class TransientProcessingException extends CatCheckException {

    public TransientProcessingException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public TransientProcessingException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }
}
