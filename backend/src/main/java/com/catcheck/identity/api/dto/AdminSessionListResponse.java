package com.catcheck.identity.api.dto;

import java.util.List;

public record AdminSessionListResponse(List<AdminSessionItem> items, int revokedSessions) {

    public static AdminSessionListResponse of(List<AdminSessionItem> items) {
        return new AdminSessionListResponse(items, 0);
    }
}
