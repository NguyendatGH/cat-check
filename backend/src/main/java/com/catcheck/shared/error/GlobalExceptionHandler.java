package com.catcheck.shared.error;

import com.catcheck.shared.i18n.MessageResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Clock;
import java.util.List;
import java.util.Locale;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageResolver messageResolver;
    private final Clock clock;

    public GlobalExceptionHandler(MessageResolver messageResolver, Clock clock) {
        this.messageResolver = messageResolver;
        this.clock = clock;
    }

    @ExceptionHandler(CatCheckException.class)
    public ProblemDetail handleCatCheckException(CatCheckException ex, Locale locale) {
        ErrorCode errorCode = ex.errorCode();
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                errorCode.status(), messageResolver.resolve(errorCode.code(), ex.args(), locale));
        problemDetail.setType(errorCode.typeUri());
        problemDetail.setTitle(errorCode.code());
        problemDetail.setProperty("errorCode", errorCode.code());
        problemDetail.setProperty("timestamp", clock.instant());
        return problemDetail;
    }

    @ExceptionHandler(RateLimitedException.class)
    public ResponseEntity<ProblemDetail> handleRateLimited(RateLimitedException ex, Locale locale) {
        ErrorCode errorCode = ex.errorCode();
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                errorCode.status(), messageResolver.resolve(errorCode.code(), ex.args(), locale));
        problemDetail.setType(errorCode.typeUri());
        problemDetail.setTitle(errorCode.code());
        problemDetail.setProperty("errorCode", errorCode.code());
        problemDetail.setProperty("timestamp", clock.instant());

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(errorCode.status());
        Long retryAfterSeconds = ex.retryAfterSeconds();
        if (retryAfterSeconds != null) {
            builder.header(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1, retryAfterSeconds)));
        }
        return builder.body(problemDetail);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, Locale locale) {
        List<ValidationViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map((FieldError fieldError) -> new ValidationViolation(fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, messageResolver.resolve("validation.failed", locale));
        problemDetail.setTitle("VALIDATION_FAILED");
        problemDetail.setProperty("errorCode", "VALIDATION_FAILED");
        problemDetail.setProperty("violations", violations);
        problemDetail.setProperty("timestamp", clock.instant());
        return problemDetail;
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNotFound(NoResourceFoundException ex, Locale locale) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, messageResolver.resolve("error.not-found", locale));
        problemDetail.setProperty("timestamp", clock.instant());
        return problemDetail;
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ProblemDetail handleMissingPart(MissingServletRequestPartException ex, Locale locale) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, messageResolver.resolve("validation.failed", locale));
        problemDetail.setTitle("VALIDATION_FAILED");
        problemDetail.setProperty("errorCode", "VALIDATION_FAILED");
        problemDetail.setProperty("violations",
                List.of(new ValidationViolation(ex.getRequestPartName(), "required")));
        problemDetail.setProperty("timestamp", clock.instant());
        return problemDetail;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParameter(MissingServletRequestParameterException ex, Locale locale) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, messageResolver.resolve("validation.failed", locale));
        problemDetail.setTitle("VALIDATION_FAILED");
        problemDetail.setProperty("errorCode", "VALIDATION_FAILED");
        problemDetail.setProperty("violations",
                List.of(new ValidationViolation(ex.getParameterName(), "required")));
        problemDetail.setProperty("timestamp", clock.instant());
        return problemDetail;
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, Locale locale) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.METHOD_NOT_ALLOWED, messageResolver.resolve("error.method-not-allowed", locale));
        problemDetail.setTitle("METHOD_NOT_ALLOWED");
        problemDetail.setProperty("errorCode", "METHOD_NOT_ALLOWED");
        problemDetail.setProperty("timestamp", clock.instant());
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        HttpHeaders headers = ex.getHeaders();
        if (headers.getAllow() != null && !headers.getAllow().isEmpty()) {
            builder.allow(headers.getAllow().toArray(new org.springframework.http.HttpMethod[0]));
        }
        return builder.body(problemDetail);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex, Locale locale) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, messageResolver.resolve("validation.failed", locale));
        problemDetail.setTitle("VALIDATION_FAILED");
        problemDetail.setProperty("errorCode", "VALIDATION_FAILED");
        problemDetail.setProperty("violations", List.of(new ValidationViolation(ex.getName(), "invalid")));
        problemDetail.setProperty("timestamp", clock.instant());
        return problemDetail;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleNotReadable(HttpMessageNotReadableException ex, Locale locale) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, messageResolver.resolve("validation.failed", locale));
        problemDetail.setTitle("VALIDATION_FAILED");
        problemDetail.setProperty("errorCode", "VALIDATION_FAILED");
        problemDetail.setProperty("timestamp", clock.instant());
        return problemDetail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex, Locale locale) {
        log.warn("Vi pham bat bien mien: {}", ex.getClass().getSimpleName());
        log.debug("Chi tiet vi pham bat bien mien", ex);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, messageResolver.resolve("validation.failed", locale));
        problemDetail.setTitle("VALIDATION_FAILED");
        problemDetail.setProperty("errorCode", "VALIDATION_FAILED");
        problemDetail.setProperty("timestamp", clock.instant());
        return problemDetail;
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ProblemDetail handleUploadTooLarge(
            org.springframework.web.multipart.MaxUploadSizeExceededException ex, Locale locale) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.PAYLOAD_TOO_LARGE, messageResolver.resolve("error.payload_too_large", locale));
        problemDetail.setTitle("PAYLOAD_TOO_LARGE");
        problemDetail.setProperty("errorCode", "PAYLOAD_TOO_LARGE");
        problemDetail.setProperty("timestamp", clock.instant());
        return problemDetail;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, Locale locale) {
        log.error("Loi khong mong muon xu ly {}", ex.getClass().getSimpleName(), RedactedThrowable.of(ex));
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, messageResolver.resolve("error.unexpected", locale));
        problemDetail.setProperty("timestamp", clock.instant());
        return problemDetail;
    }

    private record ValidationViolation(String field, String message) {
    }
}
