package com.catcheck.insight.api.dto;

import java.util.List;

public record HealthFlagListResponse(List<HealthFlagResponse> items, boolean hasMore, String nextCursor) {
}
