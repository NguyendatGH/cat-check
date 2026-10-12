package com.catcheck.shared.error;

public class NotFoundException extends CatCheckException {

    public NotFoundException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public NotFoundException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }
}
