package com.catcheck.scan.api;

import com.catcheck.shared.error.ErrorCode;
import com.catcheck.shared.error.ProblemTypes;
import org.springframework.http.HttpStatus;

import java.net.URI;

public enum ScanErrorCode implements ErrorCode {

    SCAN_METADATA_INVALID("scan-metadata-invalid", HttpStatus.BAD_REQUEST),
    IMAGE_TOO_SMALL("image-too-small", HttpStatus.BAD_REQUEST),
    IMAGE_TOO_LARGE("image-too-large", HttpStatus.PAYLOAD_TOO_LARGE),
    IMAGE_FORMAT_UNSUPPORTED("image-format-unsupported", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    IMAGE_DECODE_FAILED("image-decode-failed", HttpStatus.BAD_REQUEST),
    CAT_NOT_OWNED("cat-not-owned", HttpStatus.FORBIDDEN),
    SCAN_IN_PROGRESS("scan-in-progress", HttpStatus.CONFLICT),
    SCAN_REQUEST_NOT_FOUND("scan-request-not-found", HttpStatus.NOT_FOUND),
    SCAN_NOT_FOUND("scan-not-found", HttpStatus.NOT_FOUND),
    SCAN_ANALYSIS_NOT_FOUND("scan-analysis-not-found", HttpStatus.NOT_FOUND),
    SCAN_IMAGE_NOT_STORED("scan-image-not-stored", HttpStatus.NOT_FOUND),
    SCAN_IMAGE_EXPIRED("scan-image-expired", HttpStatus.GONE),
    SCAN_IMAGE_DSAR_REQUIRED("scan-image-dsar-required", HttpStatus.FORBIDDEN),
    SCAN_ALREADY_DELETED("scan-already-deleted", HttpStatus.CONFLICT),
    SCAN_REASSIGN_WINDOW_CLOSED("scan-reassign-window-closed", HttpStatus.CONFLICT),
    SCAN_REASSIGN_LIMIT_REACHED("scan-reassign-limit-reached", HttpStatus.CONFLICT),
    SCAN_NOT_REASSIGNABLE("scan-not-reassignable", HttpStatus.CONFLICT),
    SCAN_REASSIGN_SAME_TARGET("scan-reassign-same-target", HttpStatus.CONFLICT),
    SCAN_ALREADY_DISPUTED("scan-already-disputed", HttpStatus.CONFLICT),
    SCAN_DISPUTE_NOT_FOUND("scan-dispute-not-found", HttpStatus.NOT_FOUND),
    SCAN_CHART_UNAVAILABLE("scan-chart-unavailable", HttpStatus.UNPROCESSABLE_ENTITY),
    VISION_ENGINE_UNAVAILABLE("vision-engine-unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    SCAN_PIPELINE_ERROR("scan-pipeline-error", HttpStatus.INTERNAL_SERVER_ERROR),
    SCAN_BUSY("scan-busy", HttpStatus.TOO_MANY_REQUESTS),
    SORT_NOT_SUPPORTED_WITH_CURSOR("sort-not-supported-with-cursor", HttpStatus.BAD_REQUEST);

    private final String slug;
    private final HttpStatus status;

    ScanErrorCode(String slug, HttpStatus status) {
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
