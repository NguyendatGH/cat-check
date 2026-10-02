package com.catcheck.identity.api.dto;

import jakarta.validation.constraints.NotBlank;

/** A13 — step-up reauth (p8 §8.4.4 nhóm A). */
public record ReauthRequest(
        @NotBlank String method,
        @NotBlank String credential,
        String targetAction,
        String operationId) {
}
