package com.catcheck.credit.api.dto;

import com.catcheck.credit.application.AdminCreditService.AdminCreditAdjustmentResult;

import java.time.Instant;
import java.util.List;

/**
 * {@code POST /api/v1/admin/users/{userId}/credit-adjustments} (p8 L10).
 *
 * <p>Trả cả {@code balanceBefore} và {@code balanceAfter} để màn xác nhận của p14 §14.4.4 bước 7
 * đọc lại được đúng con số đã thay đổi, và để tổng đài dán vào ticket mà không phải gọi L9 lần
 * nữa.</p>
 *
 * @param direction     chiều đã thực hiện
 * @param amount        số credit (luôn dương)
 * @param balanceBefore số dư khả dụng trước giao dịch
 * @param balanceAfter  số dư khả dụng sau giao dịch
 * @param batchId       lô mới tạo (chiều {@code GRANT}), {@code null} ở {@code REVOKE}
 * @param expiresAt     hạn của lô mới, {@code null} ở {@code REVOKE}
 * @param batches       chi tiết từng lô bị biến động
 * @param replayed      {@code true} khi đây là replay của một {@code Idempotency-Key} đã dùng —
 *                      side effect KHÔNG được thực hiện lần hai (p8 §8.1.8)
 */
public record CreditAdjustmentResponse(
        String direction,
        int amount,
        int balanceBefore,
        int balanceAfter,
        String batchId,
        Instant expiresAt,
        List<BatchDelta> batches,
        boolean replayed
) {

    public static CreditAdjustmentResponse from(AdminCreditAdjustmentResult result) {
        return new CreditAdjustmentResponse(
                result.direction().name(),
                result.amount(),
                result.balanceBefore(),
                result.balanceAfter(),
                result.batchId() == null ? null : result.batchId().toString(),
                result.expiresAt(),
                result.batches().stream()
                        .map(delta -> new BatchDelta(
                                delta.batchId() == null ? null : delta.batchId().toString(),
                                delta.amount()))
                        .toList(),
                result.replayed());
    }

    /**
     * Một lô bị biến động.
     *
     * @param batchId id lô
     * @param amount  dương = cấp vào, âm = thu hồi ra
     */
    public record BatchDelta(String batchId, int amount) {
    }
}
