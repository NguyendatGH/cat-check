package com.catcheck.identity.api.dto;

import java.util.List;

/**
 * Phân trang offset cho các bảng admin của module identity — p8 §8.1.4 ký hiệu {@code O}.
 *
 * <p>Hình dạng giống {@code credit.api.dto.AdminPageResponse} một cách cố ý (client admin xử lý
 * mọi bảng bằng cùng một component), nhưng là type riêng: dùng chung sẽ buộc {@code identity}
 * phụ thuộc {@code credit}, điều mà {@code allowedDependencies} của cả hai module không cho và
 * {@code ModularityTests} bắt được ngay.</p>
 */
public record AdminUserPageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasMore
) {

    public static <T> AdminUserPageResponse<T> of(List<T> items, int page, int size, long totalElements) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceilDiv(totalElements, size);
        return new AdminUserPageResponse<>(
                items, page, size, totalElements, totalPages, (long) (page + 1) * size < totalElements);
    }
}
