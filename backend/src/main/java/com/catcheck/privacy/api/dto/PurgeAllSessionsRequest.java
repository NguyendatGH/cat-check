package com.catcheck.privacy.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record PurgeAllSessionsRequest(
        @NotNull UUID incidentId,
        @NotNull @Size(max = 2000) String reason) {
}
