package com.catcheck.identity.api.dto;

import jakarta.validation.constraints.NotBlank;

public record TotpVerifyRequest(
        @NotBlank String code) {
}
