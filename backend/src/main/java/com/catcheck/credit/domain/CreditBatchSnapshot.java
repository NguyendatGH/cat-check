package com.catcheck.credit.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Một dòng {@code credit_batch} đã bị {@code SELECT ... FOR UPDATE} khoá lại, chỉ đọc những gì
 * thuật toán cần: id (để {@code UPDATE}), {@code remainingAmount} (để quyết định trừ bao nhiêu),
 * {@code expiresAt} (để sắp thứ tự trừ) và {@code initialAmount} (chỉ dùng khi hoàn, để không trả
 * vượt quá số lô vốn có).
 *
 * <p>Không nhét cả entity vào domain: entity JPA nằm ở {@code ..infrastructure.persistence..}
 * (p7 §7.3.2) và đã mang chi tiết không cần cho thuật toán FEFO.</p>
 *
 * @param id             id lô
 * @param expiresAt      mốc hạn credit của lô
 * @param remainingAmount credit còn lại, không âm
 * @param initialAmount  credit lô được cấp ban đầu, mọi lần hoàn cộng vào đây đều bị chặn ở
 *                       {@code remaining <= initial}
 */
public record CreditBatchSnapshot(
        UUID id,
        Instant expiresAt,
        int remainingAmount,
        int initialAmount
) {

    public CreditBatchSnapshot {
        if (id == null) {
            throw new IllegalArgumentException("creditBatch.id phải có giá trị");
        }
        if (remainingAmount < 0) {
            throw new IllegalArgumentException("creditBatch.remainingAmount không được âm: " + id);
        }
        if (initialAmount < 0) {
            throw new IllegalArgumentException("creditBatch.initialAmount không được âm: " + id);
        }
        if (remainingAmount > initialAmount) {
            throw new IllegalArgumentException(
                    "creditBatch.remainingAmount vượt initialAmount: " + id);
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("creditBatch.expiresAt phải có giá trị: " + id);
        }
    }

    /**
     * Có còn chỗ để trả về {@code amount} credit không. Mọi lần hoàn cộng vào cùng một lô đều bị
     * {@code CHECK (remaining_amount <= initial_amount)} ở tầng DB chặn, nên phải hỏi trước ở
     * đây để trả lỗi nghiệp vụ thay vì để ngoại lệ ràng buộc nổi lên thành 500.
     */
    public boolean canRefund(int amount) {
        return amount > 0 && remainingAmount + amount <= initialAmount;
    }
}
