package com.catcheck.admin.api;

import com.catcheck.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

import java.net.URI;

public enum AdminOpsErrorCode implements ErrorCode {

    SETTING_KEY_UNKNOWN("admin/setting-key-unknown", HttpStatus.NOT_FOUND),

    JOB_NOT_FOUND("admin/job-not-found", HttpStatus.NOT_FOUND),

    JOB_NOT_MANUALLY_RUNNABLE("admin/job-not-manually-runnable", HttpStatus.CONFLICT),

    JOBS_DISABLED("admin/jobs-disabled", HttpStatus.CONFLICT),

    OUTBOX_ENTRY_NOT_FOUND("admin/outbox-entry-not-found", HttpStatus.NOT_FOUND),

    OUTBOX_ENTRY_NOT_FAILED("admin/outbox-entry-not-failed", HttpStatus.CONFLICT),

    BROADCAST_TEMPLATE_NOT_ALLOWED("admin/broadcast-template-not-allowed", HttpStatus.UNPROCESSABLE_ENTITY),

    BROADCAST_CONFIRMATION_REQUIRED("admin/broadcast-confirmation-required", HttpStatus.BAD_REQUEST);

    private static final String PROBLEM_BASE = "https://catcheck.vn/problems/";

    private final String slug;
    private final HttpStatus status;

    AdminOpsErrorCode(String slug, HttpStatus status) {
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
