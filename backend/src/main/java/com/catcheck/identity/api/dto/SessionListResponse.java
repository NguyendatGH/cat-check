package com.catcheck.identity.api.dto;

import java.util.List;

public record SessionListResponse(
        List<SessionItem> items) {
}
