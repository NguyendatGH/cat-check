package com.catcheck.identity.api.dto;

import java.util.List;

public record IdentityListResponse(
        List<IdentityItem> items) {
}
