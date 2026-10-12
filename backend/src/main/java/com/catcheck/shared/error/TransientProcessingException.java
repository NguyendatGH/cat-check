package com.catcheck.shared.error;

public class TransientProcessingException extends CatCheckException {

    public TransientProcessingException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public TransientProcessingException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }
}
