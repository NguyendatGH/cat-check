package com.catcheck.identity.api.dto;

import java.time.Instant;
import java.util.List;

/** B15 — xác nhận kích hoạt TOTP, trả recovery code (p8 §8.4.4 nhóm B). */
public record ConfirmationResponse(
        List<String> recoveryCodes,
        Instant activatedAt) {
}
