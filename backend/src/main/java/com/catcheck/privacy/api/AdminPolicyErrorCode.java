package com.catcheck.privacy.api;

import com.catcheck.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

import java.net.URI;

public enum AdminPolicyErrorCode implements ErrorCode {

    POLICY_VERSION_NOT_FOUND("policy-version-not-found", HttpStatus.NOT_FOUND),

    POLICY_VERSION_EXISTS("policy-version-exists", HttpStatus.CONFLICT),

    POLICY_VERSION_ALREADY_PUBLISHED("policy-version-already-published", HttpStatus.CONFLICT),

    POLICY_VERSION_INVALID("policy-version-invalid", HttpStatus.UNPROCESSABLE_ENTITY);

    private static final String PROBLEM_BASE = "https://catcheck.vn/problems/";

    private final String slug;
    private final HttpStatus status;

    AdminPolicyErrorCode(String slug, HttpStatus status) {
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
