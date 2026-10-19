package com.catcheck.shared.error;

public class ConflictException extends CatCheckException {

    public ConflictException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public ConflictException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }
}
