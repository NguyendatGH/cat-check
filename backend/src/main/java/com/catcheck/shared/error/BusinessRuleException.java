package com.catcheck.shared.error;

/** Vi phạm một quy tắc nghiệp vụ (ví dụ: vượt hạn mức gói cước). HTTP status do ErrorCode quyết định. */
public class BusinessRuleException extends CatCheckException {

    public BusinessRuleException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public BusinessRuleException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }
}
