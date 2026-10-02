package com.catcheck.content.api.dto;

import java.util.List;

/**
 * Envelope chuẩn của p8 §8.1.4 — dùng cho MỌI danh sách, kể cả danh sách không phân trang, để
 * client chỉ phải viết một kiểu đọc response.
 *
 * @param items  danh sách phần tử; rỗng thì trả {@code []} chứ không phải {@code null}
 * @param limit  số phần tử đã yêu cầu
 * @param hasMore còn dữ liệu phía sau không
 */
public record PageResponse<T>(List<T> items, int limit, boolean hasMore) {

    public static <T> PageResponse<T> of(List<T> items, int limit) {
        return new PageResponse<>(items, limit, false);
    }
}
