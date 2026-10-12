package com.catcheck.shared.security;

import com.catcheck.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

import java.net.URI;

public enum AdminApiErrorCode implements ErrorCode {

    ACCESS_DENIED("access-denied", HttpStatus.FORBIDDEN),

    REASON_REQUIRED("reason-required", HttpStatus.BAD_REQUEST),

    PRECONDITION_REQUIRED("precondition-required", HttpStatus.PRECONDITION_REQUIRED),

    RESOURCE_MODIFIED("resource-modified", HttpStatus.PRECONDITION_FAILED);

    private static final String PROBLEM_BASE = "https://catcheck.vn/problems/";

    private final String slug;
    private final HttpStatus status;

    AdminApiErrorCode(String slug, HttpStatus status) {
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
