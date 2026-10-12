package com.catcheck.credit.api.dto;

import com.catcheck.credit.domain.ActivationCode;

import java.time.Instant;
import java.util.UUID;

public record ActivationCodeAdminResponse(
        UUID id,
        String codePrefix,
        String packageCode,
        String productionBatch,
        Instant issuedAt,
        Instant validUntil,
        String status,
        UUID redeemedBy,
        Instant redeemedAt
) {

    public static ActivationCodeAdminResponse from(ActivationCode code) {
        return new ActivationCodeAdminResponse(
                code.id(),
                code.codePrefix(),
                code.packageCode(),
                code.productionBatch(),
                code.issuedAt(),
                code.validUntil(),
                code.status().name(),
                code.redeemedBy(),
                code.redeemedAt());
    }
}
