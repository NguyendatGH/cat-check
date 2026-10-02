package com.catcheck.scan.api.dto;

import java.util.List;

/** {@code GET /api/v1/scans} (p8 §8.4.5 E2) — phân trang con trỏ mờ, cùng khuôn với credit H3. */
public record ScanListResponse(List<ScanListItemResponse> items, boolean hasMore, String nextCursor) {
}
