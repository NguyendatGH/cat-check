package com.catcheck.colorchart.domain;

import java.util.List;

/**
 * Kết quả phân trang — kiểu không phụ thuộc Spring Data (R7: chỉ
 * {@code ..infrastructure.persistence..} được dùng {@code org.springframework.data..}).
 *
 * @param items       nội dung trang
 * @param page       số trang (0-based)
 * @param size       kích thước trang
 * @param totalElements tổng số phần tử
 * @param totalPages  tổng số trang
 * @param hasMore     còn trang tiếp theo không
 */
public record PageResult<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasMore) {

    public static <T> PageResult<T> of(List<T> items, int page, int size, long totalElements) {
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        return new PageResult<>(items, page, size, totalElements, totalPages, page < totalPages - 1);
    }
}
