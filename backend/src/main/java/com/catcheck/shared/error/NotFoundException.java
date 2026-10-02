package com.catcheck.shared.error;

/** Không tìm thấy tài nguyên được yêu cầu (thường ánh xạ sang HTTP 404 trong ErrorCode). */
public class NotFoundException extends CatCheckException {

    public NotFoundException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public NotFoundException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }
}
