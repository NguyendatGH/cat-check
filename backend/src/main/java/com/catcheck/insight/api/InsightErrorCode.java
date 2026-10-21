package com.catcheck.insight.api;

import com.catcheck.shared.error.ErrorCode;
import com.catcheck.shared.error.ProblemTypes;
import org.springframework.http.HttpStatus;

import java.net.URI;

public enum InsightErrorCode implements ErrorCode {

    HEALTH_FLAG_NOT_FOUND("health-flag-not-found", HttpStatus.NOT_FOUND),
    HEALTH_FLAG_DUPLICATE("health-flag-duplicate", HttpStatus.CONFLICT),
    MONITORING_RULE_NOT_FOUND("monitoring-rule-not-found", HttpStatus.NOT_FOUND),
    MONITORING_RULE_INVALID("monitoring-rule-invalid", HttpStatus.UNPROCESSABLE_ENTITY);

    private final String slug;
    private final HttpStatus status;

    InsightErrorCode(String slug, HttpStatus status) {
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
