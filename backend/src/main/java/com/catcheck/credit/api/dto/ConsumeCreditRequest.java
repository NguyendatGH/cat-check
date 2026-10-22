package com.catcheck.credit.api.dto;

import jakarta.validation.constraints.Min;

public record ConsumeCreditRequest(
        @Min(1) Integer credits,
        String idempotencyKey,
        String note
) {
    public ConsumeCreditRequest {
        if (credits == null || credits < 1) {
            credits = 1;
        }
    }
}
