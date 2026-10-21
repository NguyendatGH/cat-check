package com.catcheck.media.api;

import com.catcheck.shared.error.ErrorCode;
import com.catcheck.shared.error.ProblemTypes;
import org.springframework.http.HttpStatus;

import java.net.URI;

public enum MediaErrorCode implements ErrorCode {

    STORAGE_UNSUPPORTED_TYPE("storage-unsupported-type", HttpStatus.UNSUPPORTED_MEDIA_TYPE),

    STORAGE_PAYLOAD_TOO_LARGE("storage-payload-too-large", HttpStatus.PAYLOAD_TOO_LARGE),

    STORAGE_KEY_NOT_FOUND("storage-key-not-found", HttpStatus.NOT_FOUND),

    STORAGE_IO_ERROR("storage-io-error", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String slug;
    private final HttpStatus status;

    MediaErrorCode(String slug, HttpStatus status) {
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
