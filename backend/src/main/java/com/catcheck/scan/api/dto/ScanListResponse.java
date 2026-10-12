package com.catcheck.scan.api.dto;

import java.util.List;

public record ScanListResponse(List<ScanListItemResponse> items, boolean hasMore, String nextCursor) {
}
