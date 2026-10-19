package com.catcheck.shared.error;

public class BusinessRuleException extends CatCheckException {

    public BusinessRuleException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public BusinessRuleException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }
}
