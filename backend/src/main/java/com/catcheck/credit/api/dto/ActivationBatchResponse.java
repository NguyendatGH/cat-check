package com.catcheck.credit.api.dto;

import com.catcheck.credit.domain.ActivationBatchSummary;

import java.time.Instant;

/**
 * Một lô mã — L21 {@code GET /admin/activation-codes/batches}.
 *
 * @param batchId      định danh lô dùng trên đường dẫn L22/L24. <b>Chính là
 *                     {@code productionBatch}</b> — p4 không có bảng {@code activation_batch}
 *                     nào để cấp UUID riêng (handoff H15.98). Trả cả hai tên để FE không phải
 *                     biết chi tiết đó.
 * @param csvAvailable CSV mã thô còn tải được không. {@code false} sau lần tải đầu, và cũng
 *                     {@code false} sau khi backend khởi động lại (mã thô chỉ nằm trong bộ nhớ
 *                     — handoff H15.97).
 */
public record ActivationBatchResponse(
        String batchId,
        String productionBatch,
        String packageCode,
        long totalCodes,
        long issuedCodes,
        long redeemedCodes,
        long voidedCodes,
        Instant issuedAt,
        Instant validUntil,
        boolean csvAvailable
) {

    public static ActivationBatchResponse from(ActivationBatchSummary batch, boolean csvAvailable) {
        return new ActivationBatchResponse(
                batch.productionBatch(),
                batch.productionBatch(),
                batch.packageCode(),
                batch.totalCodes(),
                batch.issuedCodes(),
                batch.redeemedCodes(),
                batch.voidedCodes(),
                batch.issuedAt(),
                batch.validUntil(),
                csvAvailable);
    }
}
