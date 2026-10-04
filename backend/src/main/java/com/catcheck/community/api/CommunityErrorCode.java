package com.catcheck.community.api;

import com.catcheck.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

import java.net.URI;

public enum CommunityErrorCode implements ErrorCode {
    POST_NOT_FOUND("community/post-not-found", HttpStatus.NOT_FOUND),
    REPORT_TARGET_REQUIRED("community/report-target-required", HttpStatus.BAD_REQUEST),
    REPORT_NOT_FOUND("community/report-not-found", HttpStatus.NOT_FOUND),
    MODERATION_ACTION_INVALID("community/moderation-action-invalid", HttpStatus.BAD_REQUEST);

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
