package com.catcheck.cat.api;

import com.catcheck.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

import java.net.URI;

public enum CatErrorCode implements ErrorCode {


    CAT_NOT_FOUND("cat/not-found", HttpStatus.NOT_FOUND),

    CAT_PROFILE_LIMIT_REACHED("cat/profile-limit-reached", HttpStatus.CONFLICT),

    CAT_HARD_LIMIT_REACHED("cat/hard-limit-reached", HttpStatus.CONFLICT),

    CAT_ALREADY_DELETED("cat/already-deleted", HttpStatus.CONFLICT),

    CAT_ALREADY_ARCHIVED("cat/already-archived", HttpStatus.CONFLICT),

    CAT_BREED_UNKNOWN("cat/breed-unknown", HttpStatus.BAD_REQUEST),

    CAT_AGE_CONFLICT("cat/age-conflict", HttpStatus.BAD_REQUEST),

    SURVEY_VERSION_UNSUPPORTED("cat/survey-version-unsupported", HttpStatus.UNPROCESSABLE_ENTITY),

    CAT_NOTE_NOT_FOUND("cat/note-not-found", HttpStatus.NOT_FOUND),

    CAT_NOTE_NOT_EDITABLE("cat/note-not-editable", HttpStatus.FORBIDDEN),

    CAT_ARCHIVED("cat/archived", HttpStatus.CONFLICT),

    CAT_ALREADY_PRIMARY("cat/already-primary", HttpStatus.CONFLICT),

    CAT_PRIMARY_REQUIRES_ACTIVE("cat/primary-requires-active", HttpStatus.CONFLICT),

    CLINICAL_SIGN_INVALID("cat/clinical-sign-invalid", HttpStatus.BAD_REQUEST),

    SURVEY_NOT_FOUND("cat/survey-not-found", HttpStatus.NOT_FOUND),

    AVATAR_NOT_FOUND("cat/avatar-not-found", HttpStatus.NOT_FOUND),


    VALIDATION_FAILED("common/validation-failed", HttpStatus.BAD_REQUEST),

    RESOURCE_MODIFIED("common/resource-modified", HttpStatus.PRECONDITION_FAILED),

    PAYLOAD_TOO_LARGE("common/payload-too-large", HttpStatus.PAYLOAD_TOO_LARGE),

    AVATAR_INVALID("common/avatar-invalid", HttpStatus.BAD_REQUEST),

    ACCOUNT_RESTRICTED("common/account-restricted", HttpStatus.FORBIDDEN),

    SORT_FIELD_NOT_ALLOWED("common/sort-field-not-allowed", HttpStatus.BAD_REQUEST),

    FEATURE_NOT_IN_PLAN("common/feature-not-in-plan", HttpStatus.FORBIDDEN);

    private static final String PROBLEM_BASE = "https://catcheck.vn/problems/";

    private final String slug;
    private final HttpStatus status;

    CatErrorCode(String slug, HttpStatus status) {
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
