package com.catcheck.colorchart.domain;

import java.util.List;

public record PageResult<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasMore) {

    public static <T> PageResult<T> of(List<T> items, int page, int size, long totalElements) {
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        return new PageResult<>(items, page, size, totalElements, totalPages, page < totalPages - 1);
    }
}
