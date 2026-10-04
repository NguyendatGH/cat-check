package com.catcheck.notification.api.dto;

/**
 * Khối {@code page} của envelope danh sách chuẩn (p8 §8.1.4) — biến thể cursor.
 *
 * <p>p8 bắt buộc <b>mọi</b> danh sách dùng cùng envelope, kể cả loại không phân trang: với
 * {@code GET /push/subscriptions} (trần cứng 10 thiết bị) thì {@code nextCursor = null} và
 * {@code hasMore = false}, để client không phải viết hai kiểu code.</p>
 */
public record CursorPage(int limit, String nextCursor, boolean hasMore) {

    public static CursorPage unpaged(int size) {
        return new CursorPage(size, null, false);
    }
}
