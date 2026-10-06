package com.catcheck.credit.api.dto;

import com.catcheck.credit.domain.CreditBatchView;

import java.time.Instant;

/**
 * Một lô credit như màn quản trị thấy — phần {@code batches} của
 * {@code GET /api/v1/admin/users/{userId}/credits} (p8 L9).
 *
 * <p>Khác {@code BalanceResponse} của người dùng (p8 H2) ở chỗ <b>có {@code status}</b> và có cả
 * lô đã đóng: p5 R3 giữ lô hết hạn làm chứng từ đối soát, và câu hỏi tổng đài nhận được gần như
 * luôn là về một lô đã đóng.</p>
 *
 * @param batchId        id lô
 * @param packageCode    mã gói đã snapshot lúc tạo lô (không tham chiếu động — p5 R1)
 * @param packageVersion version cấu hình gói lúc tạo lô
 * @param initialAmount  số credit ban đầu
 * @param remainingAmount số credit còn lại
 * @param status         {@code ACTIVE} | {@code EXHAUSTED} | {@code EXPIRED}
 * @param activatedAt    lúc lô bắt đầu có hiệu lực
 * @param expiresAt      hạn credit của lô
 * @param adminGranted   lô do admin cấp tay ({@code activation_code_id IS NULL}) hay do đổi mã
 */
public record AdminCreditBatchResponse(
        String batchId,
        String packageCode,
        Integer packageVersion,
        int initialAmount,
        int remainingAmount,
        String status,
        Instant activatedAt,
        Instant expiresAt,
        Boolean adminGranted
) {

    /**
     * {@code packageVersion} và {@code adminGranted} để {@code null}: {@code CreditBatchView} —
     * record dùng chung với màn số dư của người dùng — không mang hai cột đó, và thêm chúng vào
     * nghĩa là sửa một kiểu đang được đường nóng dùng. Trả {@code null} tường minh còn hơn bịa
     * giá trị; xem handoff H15.155.
     */
    public static AdminCreditBatchResponse from(CreditBatchView batch) {
        return new AdminCreditBatchResponse(
                batch.id().toString(),
                batch.packageCode(),
                null,
                batch.initialAmount(),
                batch.remainingAmount(),
                batch.status() == null ? null : batch.status().name(),
                batch.activatedAt(),
                batch.expiresAt(),
                null);
    }
}
