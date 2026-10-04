package com.catcheck.credit.api.dto;

import com.catcheck.credit.domain.ActivationCode;

import java.time.Instant;
import java.util.UUID;

/**
 * Một dòng của L19 {@code GET /admin/activation-codes} (p8 §8.4.12, cột ở p14 §14.3.2 mục 4).
 *
 * <p><b>Không có trường nào chứa mã.</b> Kể cả {@code code_hash} cũng không được trả: nó là
 * đầu vào duy nhất để tra cứu một mã, nên lộ nó ra UI là biến mọi bản chụp màn hình admin thành
 * một phần của bí mật (p11 §11.7.4).</p>
 *
 * <p>{@code redeemedBy} là UUID, <b>không kèm email</b>: module {@code credit} không đọc
 * {@code app_user} (R6 + {@code allowedDependencies}). FE dùng id này để link sang
 * {@code /admin/users/:userId}, nơi email đã mask theo đúng REQ-RBAC-01. Xem handoff H15.99.</p>
 */
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
