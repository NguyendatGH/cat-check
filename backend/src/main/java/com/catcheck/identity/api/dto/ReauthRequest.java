package com.catcheck.identity.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ReauthRequest(
        @NotBlank String method,
        @NotBlank String credential,
        String targetAction,
        String operationId) {
}
