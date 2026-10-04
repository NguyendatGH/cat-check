package com.catcheck.place.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Public, published review fields only; account identifiers and profile data stay private. */
public record PlaceReview(UUID id, int rating, String body, OffsetDateTime createdAt) { }
