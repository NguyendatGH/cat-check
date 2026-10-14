package com.catcheck.credit.api.dto;

import com.catcheck.credit.domain.ActivationCode;
import com.catcheck.credit.domain.ActivationCodeEffectiveStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code status} là giá trị lưu DB (ISSUED/REDEEMED/VOID); {@code effectiveStatus} thêm EXPIRED khi
 * mã ISSUED đã quá {@code validUntil}, {@code expired} là cờ tương ứng.
 */
public record ActivationCodeAdminResponse(
        UUID id,
        String codePrefix,
        String packageCode,
        String productionBatch,
        Instant issuedAt,
        Instant validUntil,
        String status,
        String effectiveStatus,
        boolean expired,
        UUID redeemedBy,
        Instant redeemedAt
) {

    public static ActivationCodeAdminResponse from(ActivationCode code, ActivationCodeEffectiveStatus effectiveStatus) {
        return new ActivationCodeAdminResponse(
                code.id(),
                code.codePrefix(),
                code.packageCode(),
                code.productionBatch(),
                code.issuedAt(),
                code.validUntil(),
                code.status().name(),
                effectiveStatus.name(),
                effectiveStatus == ActivationCodeEffectiveStatus.EXPIRED,
                code.redeemedBy(),
                code.redeemedAt());
    }
}
