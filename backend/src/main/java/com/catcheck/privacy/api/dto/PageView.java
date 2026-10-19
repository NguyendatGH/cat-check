package com.catcheck.privacy.api.dto;

import java.util.List;

public record PageView<T>(
        List<T> items,
        int limit,
        String nextCursor,
        boolean hasMore
) {

    public static <T> PageView<T> of(List<T> items, int limit, String nextCursor, boolean hasMore) {
        return new PageView<>(items, limit, nextCursor, hasMore);
    }
}
