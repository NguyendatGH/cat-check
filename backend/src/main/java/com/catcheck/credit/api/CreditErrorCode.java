package com.catcheck.credit.api;

import com.catcheck.shared.error.ErrorCode;
import com.catcheck.shared.error.ProblemTypes;
import org.springframework.http.HttpStatus;

import java.net.URI;

public enum CreditErrorCode implements ErrorCode {

    CREDIT_INSUFFICIENT("credit-insufficient", HttpStatus.PAYMENT_REQUIRED),

    ACTIVATION_CODE_MALFORMED("activation-code-malformed", HttpStatus.BAD_REQUEST),

    ACTIVATION_CODE_INVALID("activation-code-invalid", HttpStatus.UNPROCESSABLE_ENTITY),

    ACTIVATION_CODE_ALREADY_USED("activation-code-already-used", HttpStatus.CONFLICT),

    ACTIVATION_CODE_EXPIRED("activation-code-expired", HttpStatus.GONE),

    ACTIVATION_RATE_LIMITED("activation-rate-limited", HttpStatus.TOO_MANY_REQUESTS),

    FEATURE_NOT_IN_PLAN("feature-not-in-plan", HttpStatus.FORBIDDEN),

    WRITE_ACCESS_EXPIRED("write-access-expired", HttpStatus.FORBIDDEN),


    ACTIVATION_BATCH_TOO_LARGE("activation-batch-too-large", HttpStatus.UNPROCESSABLE_ENTITY),

    ACTIVATION_CSV_ALREADY_DOWNLOADED("activation-csv-already-downloaded", HttpStatus.GONE),

    ACTIVATION_BATCH_EXISTS("activation-batch-exists", HttpStatus.CONFLICT),

    ACTIVATION_BATCH_NOT_FOUND("activation-batch-not-found", HttpStatus.NOT_FOUND),

    PACKAGE_PLAN_NOT_FOUND("package-plan-not-found", HttpStatus.NOT_FOUND),


    CREDIT_ADJUST_LIMIT_EXCEEDED("credit-adjust-limit-exceeded", HttpStatus.UNPROCESSABLE_ENTITY),

    CREDIT_ADJUST_EXCEEDS_BALANCE("credit-adjust-exceeds-balance", HttpStatus.CONFLICT);

    private final String slug;
    private final HttpStatus status;

    CreditErrorCode(String slug, HttpStatus status) {
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
