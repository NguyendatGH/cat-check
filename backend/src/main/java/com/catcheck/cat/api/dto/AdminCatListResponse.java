package com.catcheck.cat.api.dto;

import java.util.List;

/**
 * {@code GET /api/v1/admin/users/{userId}/cats} (p8 L4) — biến thể <b>không phân trang</b> của
 * envelope chung (p8 §8.1.4: {@code cats} có trần cứng 8/tài khoản, cột {@code Trang = —} của
 * bảng §8.4.12).
 *
 * <p>Vẫn trả {@code page} dù không phân trang, đúng quy ước §8.1.4 (<i>"vẫn trả cùng envelope
 * để client không phải viết hai kiểu code"</i>).</p>
 *
 * <p>{@link #masked} nói cho màn admin biết <b>vì sao</b> một số trường bị che: nếu không có cờ
 * này, UI phải suy ra từ vai trò người đang đăng nhập — và sẽ suy sai đúng trường hợp quan trọng
 * nhất (DPO, nhưng người dùng không còn DSAR nào mở).</p>
 *
 * @param items  hồ sơ mèo
 * @param page   envelope phân trang rút gọn
 * @param masked response này có đang che văn bản tự do không
 */
public record AdminCatListResponse(
        List<AdminCatResponse> items,
        PageInfo page,
        boolean masked
) {

    public static AdminCatListResponse of(List<AdminCatResponse> items, boolean masked) {
        return new AdminCatListResponse(items, new PageInfo(items.size(), null, false), masked);
    }

    /** Biến thể "không phân trang" của envelope §8.1.4: {@code nextCursor} null, {@code hasMore} false. */
    public record PageInfo(int limit, String nextCursor, boolean hasMore) {
    }
}
