package com.catcheck.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailChangeRequestRequest(
        @Email @NotBlank String newEmail) {
}
