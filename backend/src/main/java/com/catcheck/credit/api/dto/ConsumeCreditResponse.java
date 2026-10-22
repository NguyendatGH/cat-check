package com.catcheck.credit.api.dto;

public record ConsumeCreditResponse(
        int chargedCredits,
        int balanceAfter
) {}
