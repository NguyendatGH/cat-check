package com.catcheck.credit.api.dto;

import com.catcheck.credit.domain.CreditBalance;

import java.time.Instant;
import java.util.List;

/**
 * {@code GET /api/v1/credits/balance} (p8 H2).
 *
 * <p>Trả cả tổng lẫn từng lô với thời gian còn lại — p5 R4 nêu rõ đây là yêu cầu trực tiếp
 * từ owner, không được chỉ trả một con số.</p>
 *
 * @param availableBalance    tổng credit khả dụng
 * @param trialScansUsed      số lượt trial đã dùng
 * @param trialScansRemaining số lượt trial còn lại
 * @param batches             chi tiết từng lô, sắp theo hạn tăng dần
 */
public record BalanceResponse(
        int availableBalance,
        int trialScansUsed,
        int trialScansRemaining,
        List<BatchResponse> batches
) {

    public static BalanceResponse from(CreditBalance balance) {
        return new BalanceResponse(
                balance.availableBalance(),
                balance.trialScansUsed(),
                balance.trialScansRemaining(),
                balance.batches().stream().map(BatchResponse::from).toList());
    }

    /**
     * Một lô credit trong response số dư.
     *
     * @param remainingSeconds số giây còn lại tới hạn, {@code 0} nếu vừa hết hạn. Gửi số giây
     *                         thay vì {@code Duration} để client không phụ thuộc định dạng
     *                         ISO-8601 khi parse.
     */
    public record BatchResponse(
            String batchId,
            String packageCode,
            int initialAmount,
            int remainingAmount,
            Instant activatedAt,
            Instant expiresAt,
            long remainingSeconds
    ) {

        static BatchResponse from(CreditBalance.BatchAvailability batch) {
            return new BatchResponse(
                    batch.batchId().toString(),
                    batch.packageCode(),
                    batch.initialAmount(),
                    batch.remainingAmount(),
                    batch.activatedAt(),
                    batch.expiresAt(),
                    batch.remaining() == null ? 0L : batch.remaining().toSeconds());
        }
    }
}
