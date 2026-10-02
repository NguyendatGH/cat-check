package com.catcheck.cat.api.dto;

import java.util.List;

/**
 * Envelope {@code {items, page: {limit, nextCursor, hasMore}}} dùng cho D1
 * ({@code GET /cats}) và D16 ({@code GET /cats/{id}/notes}) — đúng hình dạng ví dụ p8 §8.5.3, và
 * khớp {@code ListResponse<T>} phía FE ({@code features/cat/hooks.ts}).
 *
 * <p>{@code nextCursor} luôn {@code null} ở M2: hai danh sách này có trần nhỏ (tối đa 8 mèo/chủ —
 * C31; ghi chú một mèo hiếm khi vượt một trang) nên phân trang kiểu offset đơn giản
 * ({@code hasMore} tính từ tổng số dòng) là đủ, không cần cursor mờ như {@code credit.ledger}.</p>
 */
public record CatPageResponse<T>(List<T> items, PageMeta page) {

    public static <T> CatPageResponse<T> of(List<T> items, int limit, boolean hasMore) {
        return new CatPageResponse<>(items, new PageMeta(limit, null, hasMore));
    }

    public record PageMeta(int limit, String nextCursor, boolean hasMore) {
    }
}
