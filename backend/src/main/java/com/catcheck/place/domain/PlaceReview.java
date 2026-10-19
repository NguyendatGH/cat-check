package com.catcheck.place.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PlaceReview(UUID id, int rating, String body, OffsetDateTime createdAt) { }
