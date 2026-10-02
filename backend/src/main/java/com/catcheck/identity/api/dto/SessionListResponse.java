package com.catcheck.identity.api.dto;

import java.util.List;

/** A14 — danh sách phiên đăng nhập của user (p8 §8.4.4 nhóm A). */
public record SessionListResponse(
        List<SessionItem> items) {
}
