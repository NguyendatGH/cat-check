package com.catcheck.admin.api;

import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.ErrorCode;
import com.catcheck.shared.error.ProblemTypes;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.util.UUID;

public final class AdminIdempotency {

    private AdminIdempotency() {
    }

    public static UUID require(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            throw new BusinessRuleException(Code.IDEMPOTENCY_KEY_MISSING);
        }
        try {
            return UUID.fromString(headerValue.strip());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException(Code.IDEMPOTENCY_KEY_INVALID);
        }
    }

    private enum Code implements ErrorCode {

        IDEMPOTENCY_KEY_MISSING("idempotency-key-missing"),
        IDEMPOTENCY_KEY_INVALID("idempotency-key-invalid");

        private final String slug;

        Code(String slug) {
            this.slug = slug;
        }

        @Override
        public String code() {
            return name();
        }

        @Override
        public HttpStatus status() {
            return HttpStatus.BAD_REQUEST;
        }

        @Override
        public URI typeUri() {
            return ProblemTypes.of(slug);
        }
    }
}
