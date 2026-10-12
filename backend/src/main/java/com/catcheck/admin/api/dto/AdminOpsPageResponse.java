package com.catcheck.admin.api.dto;

import java.util.List;

public record AdminOpsPageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasMore
) {

    public static <T> AdminOpsPageResponse<T> of(List<T> items, int page, int size, long totalElements) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceilDiv(totalElements, size);
        return new AdminOpsPageResponse<>(
                items, page, size, totalElements, totalPages, (long) (page + 1) * size < totalElements);
    }
}
