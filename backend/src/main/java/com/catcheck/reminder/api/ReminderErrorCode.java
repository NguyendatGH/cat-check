package com.catcheck.reminder.api;

import com.catcheck.shared.error.ErrorCode;
import com.catcheck.shared.error.ProblemTypes;
import org.springframework.http.HttpStatus;

import java.net.URI;

public enum ReminderErrorCode implements ErrorCode {

    REMINDER_NOT_FOUND("reminder/not-found", HttpStatus.NOT_FOUND),

    REMINDER_LIMIT_REACHED("reminder/limit-reached", HttpStatus.CONFLICT),

    REMINDER_SCHEDULE_INVALID("reminder/schedule-invalid", HttpStatus.BAD_REQUEST),

    CAT_NOT_FOUND("cat/not-found", HttpStatus.NOT_FOUND),

    FEATURE_NOT_IN_PLAN("credit/feature-not-in-plan", HttpStatus.FORBIDDEN);

    private final String slug;
    private final HttpStatus status;

    ReminderErrorCode(String slug, HttpStatus status) {
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
