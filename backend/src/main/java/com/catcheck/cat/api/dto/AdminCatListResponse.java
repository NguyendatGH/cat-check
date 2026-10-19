package com.catcheck.cat.api.dto;

import java.util.List;

public record AdminCatListResponse(
        List<AdminCatResponse> items,
        PageInfo page,
        boolean masked
) {

    public static AdminCatListResponse of(List<AdminCatResponse> items, boolean masked) {
        return new AdminCatListResponse(items, new PageInfo(items.size(), null, false), masked);
    }

    public record PageInfo(int limit, String nextCursor, boolean hasMore) {
    }
}
