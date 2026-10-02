package com.catcheck.identity.api.dto;

import java.util.List;

/** B9 — danh sách phương thức đăng nhập đã liên kết (p8 §8.4.4 nhóm B). */
public record IdentityListResponse(
        List<IdentityItem> items) {
}
