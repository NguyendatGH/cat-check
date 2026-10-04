package com.catcheck.place.api;

import com.catcheck.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

import java.net.URI;

public enum PlaceErrorCode implements ErrorCode {
    PLACE_NOT_FOUND("place/not-found", HttpStatus.NOT_FOUND),
    BOOKING_SLOT_TAKEN("place/booking-slot-taken", HttpStatus.CONFLICT);

    private final String slug;
    private final HttpStatus status;

    PlaceErrorCode(String slug, HttpStatus status) { this.slug = slug; this.status = status; }
    @Override public String code() { return name(); }
    @Override public HttpStatus status() { return status; }
    @Override public URI typeUri() { return URI.create("https://catcheck.vn/problems/" + slug); }
}
