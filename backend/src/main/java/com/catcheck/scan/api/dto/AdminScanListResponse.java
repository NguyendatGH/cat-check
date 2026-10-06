package com.catcheck.scan.api.dto;

import java.util.List;

/**
 * {@code GET /api/v1/admin/users/{userId}/scans} (p8 L5) — envelope phân trang <b>offset</b>
 * đúng biến thể của p8 §8.1.4 (cột {@code Trang = O} của bảng §8.4.12).
 *
 * <p>{@link #masked} nói cho màn admin biết <b>vì sao</b> một số trường bị che: nếu không có cờ
 * này, UI phải suy ra từ vai trò người đang đăng nhập — và sẽ suy sai đúng trường hợp quan trọng
 * nhất (DPO, nhưng người dùng không còn DSAR nào mở).</p>
 *
 * @param items  các dòng của trang
 * @param page   thông tin phân trang
 * @param masked response này có đang che văn bản tự do không
 */
public record AdminScanListResponse(
        List<AdminScanItemResponse> items,
        PageInfo page,
        boolean masked
) {

    public static AdminScanListResponse of(
            List<AdminScanItemResponse> items, int page, int size, long totalElements, boolean masked) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceilDiv(totalElements, size);
        return new AdminScanListResponse(
                items,
                new PageInfo(page, size, totalElements, totalPages,
                        (long) (page + 1) * size < totalElements),
                masked);
    }

    /** Biến thể offset của envelope phân trang chung (p8 §8.1.4). */
    public record PageInfo(
            int number, int size, long totalElements, int totalPages, boolean hasMore) {
    }
}
