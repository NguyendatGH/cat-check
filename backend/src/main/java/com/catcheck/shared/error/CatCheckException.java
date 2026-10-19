package com.catcheck.shared.error;

public abstract class CatCheckException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Object[] args;

    protected CatCheckException(ErrorCode errorCode, Object... args) {
        super(errorCode.code());
        this.errorCode = errorCode;
        this.args = args;
    }

    protected CatCheckException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode.code(), cause);
        this.errorCode = errorCode;
        this.args = args;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Object[] args() {
        return args;
    }
}
