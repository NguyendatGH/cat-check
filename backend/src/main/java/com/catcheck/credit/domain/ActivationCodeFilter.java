package com.catcheck.credit.domain;

import java.time.Instant;

/**
 * Bộ lọc tra cứu mã. {@code expired} (kèm {@code asOf}) chỉ có nghĩa khi cần tách mã ISSUED còn hạn
 * và đã hết hạn kích hoạt; {@code null} = không lọc theo hạn.
 */
public record ActivationCodeFilter(
        String codePrefix,
        String packageCode,
        ActivationCodeStatus status,
        String productionBatch,
        Boolean expired,
        Instant asOf
) {

    public static ActivationCodeFilter none() {
        return new ActivationCodeFilter(null, null, null, null);
    }

    public ActivationCodeFilter(String codePrefix, String packageCode, ActivationCodeStatus status,
                                String productionBatch) {
        this(codePrefix, packageCode, status, productionBatch, null, null);
    }

    /** Lọc theo trạng thái suy diễn: ISSUED = chưa dùng & còn hạn, EXPIRED = chưa dùng & quá hạn. */
    public static ActivationCodeFilter of(String codePrefix, String packageCode,
                                          ActivationCodeEffectiveStatus effectiveStatus,
                                          String productionBatch, Instant now) {
        if (effectiveStatus == null) {
            return new ActivationCodeFilter(codePrefix, packageCode, null, productionBatch);
        }
        return switch (effectiveStatus) {
            case ISSUED -> new ActivationCodeFilter(codePrefix, packageCode, ActivationCodeStatus.ISSUED,
                    productionBatch, Boolean.FALSE, now);
            case EXPIRED -> new ActivationCodeFilter(codePrefix, packageCode, ActivationCodeStatus.ISSUED,
                    productionBatch, Boolean.TRUE, now);
            case REDEEMED -> new ActivationCodeFilter(codePrefix, packageCode, ActivationCodeStatus.REDEEMED,
                    productionBatch);
            case VOID -> new ActivationCodeFilter(codePrefix, packageCode, ActivationCodeStatus.VOID,
                    productionBatch);
        };
    }

    public ActivationCodeFilter {
        codePrefix = blankToNull(codePrefix);
        packageCode = blankToNull(packageCode);
        productionBatch = blankToNull(productionBatch);
        if (expired != null && asOf == null) {
            throw new IllegalArgumentException("Lọc theo hạn cần mốc thời gian asOf");
        }
    }

    /** Khớp đúng điều kiện SQL của adapter, dùng cho test double và kiểm tra trong bộ nhớ. */
    public boolean matches(ActivationCode code) {
        return (codePrefix == null || (code.codePrefix() != null && code.codePrefix().startsWith(codePrefix)))
                && (packageCode == null || packageCode.equals(code.packageCode()))
                && (status == null || status == code.status())
                && (productionBatch == null || productionBatch.equals(code.productionBatch()))
                && (expired == null || expired == code.isIssuanceExpiredAt(asOf));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
