package com.catcheck.shared.error;

/** Vượt giới hạn tần suất (Bucket4j) - thường HTTP 429. Mang kèm retryAfterSeconds nếu biết. */
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
