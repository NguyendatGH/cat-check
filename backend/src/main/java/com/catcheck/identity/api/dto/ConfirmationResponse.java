package com.catcheck.identity.api.dto;

import java.time.Instant;
import java.util.List;

public record ConfirmationResponse(
        List<String> recoveryCodes,
        Instant activatedAt) {
}
