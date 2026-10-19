package com.catcheck.credit.api.dto;

import java.util.List;

public record AdminPageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasMore
) {

    public static <T> AdminPageResponse<T> of(List<T> items, int page, int size, long totalElements) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceilDiv(totalElements, size);
        return new AdminPageResponse<>(
                items, page, size, totalElements, totalPages, (long) (page + 1) * size < totalElements);
    }
}
