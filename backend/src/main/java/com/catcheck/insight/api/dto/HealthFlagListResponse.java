package com.catcheck.insight.api.dto;

import java.util.List;

/** {@code GET /api/v1/health-flags} (p8 §8.4.7 G1). */
public record HealthFlagListResponse(List<HealthFlagResponse> items, boolean hasMore, String nextCursor) {
}
