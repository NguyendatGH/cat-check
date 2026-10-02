package com.catcheck.identity.api.dto;

import java.time.Instant;

/** A13 — kết quả step-up reauth (p8 §8.4.4 nhóm A). */
public record ReauthResponse(
        String scope,
        Instant reauthExpiresAt,
        String method) {
}
