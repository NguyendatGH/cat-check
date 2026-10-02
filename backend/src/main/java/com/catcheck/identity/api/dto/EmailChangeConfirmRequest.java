package com.catcheck.identity.api.dto;

import jakarta.validation.constraints.NotBlank;

/** B8 — xác nhận đổi email (p8 §8.4.4 nhóm B). */
public record EmailChangeConfirmRequest(
        @NotBlank String otpTicket) {
}
