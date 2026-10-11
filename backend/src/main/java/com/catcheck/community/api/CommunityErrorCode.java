package com.catcheck.community.api;

import com.catcheck.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

import java.net.URI;

public enum CommunityErrorCode implements ErrorCode {
    POST_NOT_FOUND("community/post-not-found", HttpStatus.NOT_FOUND),
    REPORT_TARGET_REQUIRED("community/report-target-required", HttpStatus.BAD_REQUEST),
    REPORT_DUPLICATE("community/report-duplicate", HttpStatus.CONFLICT),
    REPORT_NOT_FOUND("community/report-not-found", HttpStatus.NOT_FOUND),
    MODERATION_ACTION_INVALID("community/moderation-action-invalid", HttpStatus.BAD_REQUEST),
    /**
     * I19 (p4 §4.4.9) — tài khoản {@code RESTRICTED} CHỈ ĐỌC. Tên hằng trùng
     * {@code cat.api.CatErrorCode.ACCOUNT_RESTRICTED}/{@code identity.api.IdentityErrorCode
     * .ACCOUNT_RESTRICTED} có chủ đích (cùng ý nghĩa) — KHÔNG đăng ký bean cho hằng này trong
     * community (không có {@code CommunityErrorCodeConfiguration}) vì
     * {@code IdentityErrorCodeConfiguration} đã đăng ký {@code code()="ACCOUNT_RESTRICTED"};
     * đăng ký lại sẽ làm {@code ErrorCodeRegistry.validate} ném {@code IllegalStateException}
     * lúc khởi động (xem javadoc {@code CatErrorCodeConfiguration}). {@code GlobalExceptionHandler}
     * đọc status/typeUri thẳng trên instance bị ném, không qua registry, nên vẫn hoạt động đúng.
     */
    ACCOUNT_RESTRICTED("common/account-restricted", HttpStatus.FORBIDDEN),
    POST_RATE_LIMITED("community/post-rate-limited", HttpStatus.TOO_MANY_REQUESTS),
    COMMENT_RATE_LIMITED("community/comment-rate-limited", HttpStatus.TOO_MANY_REQUESTS),
    REPORT_RATE_LIMITED("community/report-rate-limited", HttpStatus.TOO_MANY_REQUESTS);

    private final String slug;
    private final HttpStatus status;

    CommunityErrorCode(String slug, HttpStatus status) {
        this.slug = slug;
        this.status = status;
    }

    @Override
    public String code() { return name(); }

    @Override
    public HttpStatus status() { return status; }

    @Override
    public URI typeUri() { return URI.create("https://catcheck.vn/problems/" + slug); }
}
