package com.catcheck.notification.api.dto;

public record CursorPage(int limit, String nextCursor, boolean hasMore) {

    public static CursorPage unpaged(int size) {
        return new CursorPage(size, null, false);
    }
}
