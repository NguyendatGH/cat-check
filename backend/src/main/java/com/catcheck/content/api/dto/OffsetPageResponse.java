package com.catcheck.content.api.dto;

import com.catcheck.content.application.CareTipAdminService.AdminPage;

import java.util.List;

/**
 * Biến thể offset của envelope p8 §8.1.4 — dùng cho các bảng admin cần nhảy trang và tổng số dòng
 * ("trang 7/23", "tổng 4 512 bản ghi"). {@code nextCursor} không có ở biến này.
 */
public record OffsetPageResponse<T>(
        List<T> items,
        int number,
        int size,
        long totalElements,
        int totalPages,
        boolean hasMore
) {

    public static <T> OffsetPageResponse<T> of(AdminPage<T> page) {
        return new OffsetPageResponse<>(
                page.items(), page.number(), page.size(), page.totalElements(), page.totalPages(), page.hasMore());
    }
}
