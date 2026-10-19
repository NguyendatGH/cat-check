package com.catcheck.export.api;

import com.catcheck.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

import java.net.URI;

public enum ExportErrorCode implements ErrorCode {

    EXPORT_JOB_IN_PROGRESS("export-job-in-progress", HttpStatus.CONFLICT),
    EXPORT_NO_DATA("export-no-data", HttpStatus.UNPROCESSABLE_ENTITY),
    EXPORT_JOB_NOT_FOUND("export-job-not-found", HttpStatus.NOT_FOUND),
    EXPORT_NOT_READY("export-not-ready", HttpStatus.CONFLICT),
    EXPORT_EXPIRED("export-expired", HttpStatus.GONE),
    EXPORT_RANGE_INVALID("export-range-invalid", HttpStatus.BAD_REQUEST),

    FEATURE_NOT_IN_PLAN("credit/feature-not-in-plan", HttpStatus.FORBIDDEN);

    private static final String PROBLEM_BASE = "https://catcheck.vn/problems/";

    private final String slug;
    private final HttpStatus status;

    ExportErrorCode(String slug, HttpStatus status) {
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
