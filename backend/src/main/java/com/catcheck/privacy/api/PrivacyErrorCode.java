package com.catcheck.privacy.api;

import com.catcheck.shared.error.ErrorCode;
import com.catcheck.shared.error.ProblemTypes;
import org.springframework.http.HttpStatus;

import java.net.URI;

public enum PrivacyErrorCode implements ErrorCode {


    CONSENT_MANDATORY_CANNOT_WITHDRAW("consent-mandatory-cannot-withdraw", HttpStatus.CONFLICT),

    CONSENT_REQUIRED("consent-required", HttpStatus.FORBIDDEN),

    POLICY_REACCEPT_REQUIRED("policy-reaccept-required", HttpStatus.FORBIDDEN),

    POLICY_VERSION_NOT_FOUND("policy-version-not-found", HttpStatus.NOT_FOUND),


    DSAR_NOT_FOUND("dsar-not-found", HttpStatus.NOT_FOUND),

    RETENTION_POLICY_NOT_FOUND("retention-policy-not-found", HttpStatus.NOT_FOUND),

    DSAR_IDENTITY_VERIFICATION_REQUIRED("dsar-identity-verification-required", HttpStatus.FORBIDDEN),

    DSAR_EXPORT_RATE_LIMITED("dsar-export-rate-limited", HttpStatus.TOO_MANY_REQUESTS),

    DSAR_EXPORT_NOT_READY("dsar-export-not-ready", HttpStatus.CONFLICT),

    DSAR_EXPORT_EXPIRED("dsar-export-expired", HttpStatus.GONE),

    DSAR_EXPORT_ALREADY_DOWNLOADED("dsar-export-already-downloaded", HttpStatus.GONE),

    DELETION_ALREADY_REQUESTED("deletion-already-requested", HttpStatus.CONFLICT),

    DELETION_GRACE_EXPIRED("deletion-grace-expired", HttpStatus.CONFLICT),

    DELETION_NOT_REQUESTED("deletion-not-requested", HttpStatus.CONFLICT),

    DSAR_REQUEST_TYPE_UNSUPPORTED("dsar-request-type-unsupported", HttpStatus.BAD_REQUEST),

    RESTRICTION_ALREADY_ACTIVE("restriction-already-active", HttpStatus.CONFLICT),

    DSAR_SELF_APPROVAL_FORBIDDEN("dsar-self-approval-forbidden", HttpStatus.CONFLICT),


    RETENTION_LIMIT_EXCEEDED("retention-limit-exceeded", HttpStatus.UNPROCESSABLE_ENTITY),

    SECURITY_INCIDENT_NOT_FOUND("security-incident-not-found", HttpStatus.NOT_FOUND),


    VALIDATION_FAILED("validation-failed", HttpStatus.BAD_REQUEST),

    FORBIDDEN("forbidden", HttpStatus.FORBIDDEN),

    RATE_LIMITED("rate-limited", HttpStatus.TOO_MANY_REQUESTS),

    CONSENT_PURPOSE_UNKNOWN("consent-purpose-unknown", HttpStatus.BAD_REQUEST),

    AVATAR_INVALID("avatar-invalid", HttpStatus.BAD_REQUEST),

    PAGINATION_CURSOR_INVALID("pagination-cursor-invalid", HttpStatus.BAD_REQUEST);

    private final String slug;
    private final HttpStatus status;

    PrivacyErrorCode(String slug, HttpStatus status) {
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
