package com.catcheck.shared.error;

/** Xung đột trạng thái (ví dụ: cập nhật một bản ghi đã bị thay đổi/xóa) - thường HTTP 409. */
public class ConflictException extends CatCheckException {

    public ConflictException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public ConflictException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }
}
