package com.catcheck.shared.error;

/** Người dùng đã xác thực nhưng không đủ quyền thực hiện hành động - thường HTTP 403. */
public class PermissionDeniedException extends CatCheckException {

    public PermissionDeniedException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public PermissionDeniedException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }
}
