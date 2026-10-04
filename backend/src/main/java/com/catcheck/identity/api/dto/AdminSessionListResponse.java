package com.catcheck.identity.api.dto;

import java.util.List;

/** Danh sách phiên đang mở của một người dùng — L11. */
public record AdminSessionListResponse(List<AdminSessionItem> items, int revokedSessions) {

    public static AdminSessionListResponse of(List<AdminSessionItem> items) {
        return new AdminSessionListResponse(items, 0);
    }
}
