package com.catcheck.place.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record PlaceReviewRequest(@Min(1) @Max(5) int rating, @Size(max = 2000) String body) { }
