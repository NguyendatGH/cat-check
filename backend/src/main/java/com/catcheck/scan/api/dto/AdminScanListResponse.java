package com.catcheck.scan.api.dto;

import java.util.List;

public record AdminScanListResponse(
        List<AdminScanItemResponse> items,
        PageInfo page,
        boolean masked
) {

    public static AdminScanListResponse of(
            List<AdminScanItemResponse> items, int page, int size, long totalElements, boolean masked) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceilDiv(totalElements, size);
        return new AdminScanListResponse(
                items,
                new PageInfo(page, size, totalElements, totalPages,
                        (long) (page + 1) * size < totalElements),
                masked);
    }

    public record PageInfo(
            int number, int size, long totalElements, int totalPages, boolean hasMore) {
    }
}
