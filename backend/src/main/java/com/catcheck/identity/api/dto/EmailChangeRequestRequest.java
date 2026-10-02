package com.catcheck.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** B7 — yêu cầu đổi email (p8 §8.4.4 nhóm B). */
public record EmailChangeRequestRequest(
        @Email @NotBlank String newEmail) {
}
