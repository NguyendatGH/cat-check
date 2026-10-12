package com.catcheck.content.api.dto;

import java.util.List;

public record PageResponse<T>(List<T> items, int limit, boolean hasMore) {

    public static <T> PageResponse<T> of(List<T> items, int limit) {
        return new PageResponse<>(items, limit, false);
    }
}
