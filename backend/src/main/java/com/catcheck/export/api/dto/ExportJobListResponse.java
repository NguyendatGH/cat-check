package com.catcheck.export.api.dto;

import java.util.List;

public record ExportJobListResponse(List<ExportJobResponse> items, boolean hasMore, String nextCursor) {
}
