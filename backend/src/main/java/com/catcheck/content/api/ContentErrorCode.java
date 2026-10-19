package com.catcheck.content.api;

import com.catcheck.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

import java.net.URI;

public enum ContentErrorCode implements ErrorCode {

    CONTENT_NOT_FOUND("content-not-found", HttpStatus.NOT_FOUND),

    CONTENT_SOURCE_REQUIRED("content-source-required", HttpStatus.UNPROCESSABLE_ENTITY),

    CONTENT_SELF_APPROVAL_FORBIDDEN("content-self-approval-forbidden", HttpStatus.CONFLICT),

    CONTENT_NOT_EDITABLE("content-not-editable", HttpStatus.CONFLICT),

    CONTENT_INVALID_STATE("content-invalid-state", HttpStatus.CONFLICT),

    CONTENT_SLUG_TAKEN("content-slug-taken", HttpStatus.CONFLICT),

    REASON_REQUIRED("reason-required", HttpStatus.BAD_REQUEST),

    ADMIN_ROLE_REQUIRED("admin-role-required", HttpStatus.FORBIDDEN),

    VALIDATION_FAILED("validation-failed", HttpStatus.BAD_REQUEST);

    private static final String PROBLEM_BASE = "https://catcheck.vn/problems/";

    private final String slug;
    private final HttpStatus status;

    ContentErrorCode(String slug, HttpStatus status) {
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
        return URI.create(PROBLEM_BASE + slug);
    }
}
