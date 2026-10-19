package com.catcheck.identity.api.dto;

import java.time.Instant;

public record ReauthResponse(
        String scope,
        Instant reauthExpiresAt,
        String method) {
}
