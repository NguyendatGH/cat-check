package com.catcheck.shared.error;

public class PermissionDeniedException extends CatCheckException {

    public PermissionDeniedException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public PermissionDeniedException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }
}
