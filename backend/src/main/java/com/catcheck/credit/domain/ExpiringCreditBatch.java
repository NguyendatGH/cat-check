package com.catcheck.credit.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Một lô credit mà job nền vừa khoá được, kèm <b>đủ</b> thông tin để ghi dòng ledger hoặc dựng
 * thông báo nhắc mà không phải truy vấn lại.
 *
 * <p>Khác {@link CreditBatchSnapshot} ở đúng một điểm quan trọng: có {@code userId}. FEFO luôn
 * chạy trong phạm vi một người dùng đã biết, nên {@code CreditBatchSnapshot} cố ý không mang
 * cột đó; job nền thì quét toàn hệ thống, và {@code credit_ledger.user_id} là cột NOT NULL nên
 * thiếu nó là không ghi được sổ cái.</p>
 *
 * @param id              id lô
 * @param userId          chủ sở hữu credit — bắt buộc để ghi {@code credit_ledger.user_id}
 * @param expiresAt       mốc hết hạn của lô
 * @param remainingAmount credit còn lại, {@code > 0} (điều kiện lọc của cả hai job nhóm A)
 * @param initialAmount   credit lô được cấp ban đầu
 */
public record ExpiringCreditBatch(
        UUID id,
        UUID userId,
        Instant expiresAt,
        int remainingAmount,
        int initialAmount
) {

    public ExpiringCreditBatch {
        if (id == null || userId == null || expiresAt == null) {
            throw new IllegalArgumentException("expiringCreditBatch thiếu trường bắt buộc");
        }
        if (remainingAmount <= 0) {
            // Cả ExpireCreditBatchesJob và CreditExpiringReminderJob đều lọc remaining_amount > 0
            // (p12 §12.6.2). Lô đã cạn đi vào đây nghĩa là câu SQL sai, không phải dữ liệu lạ.
            throw new IllegalArgumentException(
                    "expiringCreditBatch.remainingAmount phải > 0: " + id);
        }
        if (remainingAmount > initialAmount) {
            throw new IllegalArgumentException(
                    "expiringCreditBatch.remainingAmount vượt initialAmount: " + id);
        }
    }
}
