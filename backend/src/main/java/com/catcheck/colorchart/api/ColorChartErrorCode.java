package com.catcheck.colorchart.api;

import com.catcheck.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

import java.net.URI;

public enum ColorChartErrorCode implements ErrorCode {

    COLOR_CHART_NOT_FOUND("color-chart-not-found", HttpStatus.NOT_FOUND),

    COLOR_CHART_IN_USE("color-chart-in-use", HttpStatus.CONFLICT),

    COLOR_CHART_INCOMPLETE("color-chart-incomplete", HttpStatus.UNPROCESSABLE_ENTITY),

    COLOR_CHART_CODE_TAKEN("color-chart-code-taken", HttpStatus.CONFLICT),

    PH_BAND_NOT_FOUND("ph-band-not-found", HttpStatus.NOT_FOUND),

    PH_BAND_OVERLAP("ph-band-overlap", HttpStatus.CONFLICT),

    REASON_REQUIRED("reason-required", HttpStatus.BAD_REQUEST),

    ADMIN_ROLE_REQUIRED("admin-role-required", HttpStatus.FORBIDDEN),

    VALIDATION_FAILED("validation-failed", HttpStatus.BAD_REQUEST),

    PRECONDITION_REQUIRED("precondition-required", HttpStatus.PRECONDITION_REQUIRED),

    RESOURCE_MODIFIED("resource-modified", HttpStatus.PRECONDITION_FAILED);

    private static final String PROBLEM_BASE = "https://catcheck.vn/problems/";

    private final String slug;
    private final HttpStatus status;

    ColorChartErrorCode(String slug, HttpStatus status) {
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
