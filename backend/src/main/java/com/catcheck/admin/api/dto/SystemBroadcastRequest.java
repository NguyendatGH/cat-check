package com.catcheck.admin.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record SystemBroadcastRequest(
        @NotBlank @Size(max = 48) String templateCode,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 4_000) String body,
        Map<String, Object> payload,
        Boolean dryRun,
        Boolean confirmed,
        @NotBlank @Size(max = 500) String reason) {

    public SystemBroadcastRequest {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    public boolean dryRunOrFalse() {
        return Boolean.TRUE.equals(dryRun);
    }

    public boolean confirmedOrFalse() {
        return Boolean.TRUE.equals(confirmed);
    }
}
