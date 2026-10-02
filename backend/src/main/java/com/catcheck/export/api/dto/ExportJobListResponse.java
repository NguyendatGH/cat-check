package com.catcheck.export.api.dto;

import java.util.List;

/** {@code GET /api/v1/exports} (p8 §8.4.10 J2). */
public record ExportJobListResponse(List<ExportJobResponse> items, boolean hasMore, String nextCursor) {
}
