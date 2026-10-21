package com.catcheck.notification.api;

import com.catcheck.shared.error.ErrorCode;
import com.catcheck.shared.error.ProblemTypes;
import org.springframework.http.HttpStatus;

import java.net.URI;

public enum NotificationErrorCode implements ErrorCode {

    NOTIFICATION_NOT_FOUND("notification/not-found", HttpStatus.NOT_FOUND),

    PUSH_SUBSCRIPTION_INVALID("push/subscription-invalid", HttpStatus.BAD_REQUEST),

    PUSH_SUBSCRIPTION_LIMIT("push/subscription-limit", HttpStatus.CONFLICT),

    NOTIFICATION_TEMPLATE_UNKNOWN("notification/template-unknown", HttpStatus.BAD_REQUEST),

    CONSENT_REQUIRED("consent-required", HttpStatus.FORBIDDEN),

    PAGINATION_CURSOR_INVALID("pagination-cursor-invalid", HttpStatus.BAD_REQUEST),

    VALIDATION_FAILED("validation-failed", HttpStatus.BAD_REQUEST);

    private final String slug;
    private final HttpStatus status;

    NotificationErrorCode(String slug, HttpStatus status) {
        this.slug = slug;
        this.status = status;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public URI typeUri() {
        return ProblemTypes.of(slug);
    }
}
