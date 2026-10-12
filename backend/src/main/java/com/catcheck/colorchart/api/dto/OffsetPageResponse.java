package com.catcheck.colorchart.api.dto;

import java.util.List;

public record OffsetPageResponse<T>(
        List<T> items,
        int number,
        int size,
        long totalElements,
        int totalPages,
        boolean hasMore) {
}
