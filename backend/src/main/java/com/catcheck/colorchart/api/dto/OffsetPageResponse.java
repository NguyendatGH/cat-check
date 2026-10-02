package com.catcheck.colorchart.api.dto;

import java.util.List;

/**
 * Biến thể offset của envelope p8 §8.1.4 — dùng cho các bảng admin cần nhảy trang và tổng số dòng
 * ("trang 7/23", "tổng 4 512 bản ghi").
 */
public record OffsetPageResponse<T>(
        List<T> items,
        int number,
        int size,
        long totalElements,
        int totalPages,
        boolean hasMore) {
}
