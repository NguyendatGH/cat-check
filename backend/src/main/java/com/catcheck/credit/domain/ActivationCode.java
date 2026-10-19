package com.catcheck.credit.domain;

import java.time.Instant;
import java.util.UUID;

public record ActivationCode(
        UUID id,
        String codeHash,
        short pepperVersion,
        String codePrefix,
        String packageCode,
        String productionBatch,
        Instant issuedAt,
        Instant validUntil,
        ActivationCodeStatus status,
        UUID redeemedBy,
        Instant redeemedAt
) {

    public ActivationCode {
        if (id == null || codeHash == null || packageCode == null || issuedAt == null || status == null) {
            throw new IllegalArgumentException("activationCode thiếu trường bắt buộc");
        }
        if (codeHash.length() != 64) {
            throw new IllegalArgumentException("activationCode.codeHash phải đúng 64 ký tự hex");
        }
        if (pepperVersion < 1) {
            throw new IllegalArgumentException("activationCode.pepperVersion phải >= 1");
        }
    }

    public boolean isIssuanceExpiredAt(Instant now) {
        return validUntil != null && !validUntil.isAfter(now);
    }

    /** Trạng thái hiển thị cho admin: mã ISSUED đã quá {@code validUntil} được coi là EXPIRED. */
    public ActivationCodeEffectiveStatus effectiveStatusAt(Instant now) {
        return switch (status) {
            case REDEEMED -> ActivationCodeEffectiveStatus.REDEEMED;
            case VOID -> ActivationCodeEffectiveStatus.VOID;
            case ISSUED -> isIssuanceExpiredAt(now)
                    ? ActivationCodeEffectiveStatus.EXPIRED : ActivationCodeEffectiveStatus.ISSUED;
        };
    }
}
