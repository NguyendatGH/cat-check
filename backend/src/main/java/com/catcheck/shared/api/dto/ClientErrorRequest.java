package com.catcheck.shared.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClientErrorRequest(
        @Size(max = 2_000) String message,
        @Size(max = 6_000) String stack,
        @Size(max = 64) String appVersion,
        @Size(max = 64) String traceId,
        @Size(max = 512) String route) {
}
