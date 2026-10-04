package com.catcheck.place.api.dto;

import com.catcheck.place.domain.PlaceReview;

import java.time.OffsetDateTime;

/** Deliberately excludes reviewer identity and all account/profile fields. */
public record PlaceReviewResponse(String id, int rating, String body, OffsetDateTime createdAt) {
    public static PlaceReviewResponse from(PlaceReview review) {
        return new PlaceReviewResponse(review.id().toString(), review.rating(), review.body(), review.createdAt());
    }
}
