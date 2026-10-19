package com.catcheck.cat.api.dto;

import java.util.List;

public record CatPageResponse<T>(List<T> items, PageMeta page) {

    public static <T> CatPageResponse<T> of(List<T> items, int limit, boolean hasMore) {
        return new CatPageResponse<>(items, new PageMeta(limit, null, hasMore));
    }

    public record PageMeta(int limit, String nextCursor, boolean hasMore) {
    }
}
