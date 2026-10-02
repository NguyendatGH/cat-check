package com.catcheck.privacy.api.dto;

import java.util.List;

/**
 * Envelope thống nhất cho MỌI danh sách (p8 §8.1.4) — biến thể cursor.
 *
 * @param items      trang dữ liệu
 * @param limit      kích thước trang thực tế
 * @param nextCursor cursor trang sau — null khi hết dữ liệu
 * @param hasMore    còn trang sau không
 */
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
