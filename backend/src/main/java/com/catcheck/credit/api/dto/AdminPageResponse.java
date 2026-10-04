package com.catcheck.credit.api.dto;

import java.util.List;

/**
 * Phân trang kiểu <b>offset</b> cho các bảng admin — p8 §8.1.4 ký hiệu {@code O}.
 *
 * <p>Khác cursor ({@code C}) dùng cho dòng thời gian của người dùng cuối: màn admin cần nhảy
 * tới trang bất kỳ và cần biết tổng số dòng, hai thứ cursor không cho.</p>
 *
 * @param items         các dòng của trang hiện tại
 * @param page          số trang, đếm từ 0
 * @param size          số dòng mỗi trang
 * @param totalElements tổng số dòng khớp bộ lọc
 * @param totalPages    tổng số trang
 * @param hasMore       còn trang sau không
 */
public record AdminPageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasMore
) {

    public static <T> AdminPageResponse<T> of(List<T> items, int page, int size, long totalElements) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceilDiv(totalElements, size);
        return new AdminPageResponse<>(
                items, page, size, totalElements, totalPages, (long) (page + 1) * size < totalElements);
    }
}
