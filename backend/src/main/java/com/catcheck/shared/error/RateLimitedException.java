package com.catcheck.shared.error;

public class RateLimitedException extends CatCheckException {

    private final Long retryAfterSeconds;

    public RateLimitedException(ErrorCode errorCode, Long retryAfterSeconds, Object... args) {
        super(errorCode, args);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
