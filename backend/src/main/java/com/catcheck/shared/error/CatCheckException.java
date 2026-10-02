package com.catcheck.shared.error;

/**
 * Lớp cha trừu tượng cho mọi exception nghiệp vụ trong CatCheck. Mang theo {@link ErrorCode}
 * (để {@link GlobalExceptionHandler} quyết định HTTP status + type URI) và {@code args} (tham
 * số dùng để nội suy message đã dịch qua {@code MessageResolver} — KHÔNG đưa PII vào args).
 */
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
