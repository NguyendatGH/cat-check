package com.catcheck.credit.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Một lô credit đầy đủ để hiển thị — dữ liệu đọc từ {@code credit_batch}, không khoá.
 * Dùng cho {@code GET /credits/balance} (p5 R4: phải thấy được từng lô còn bao nhiêu và còn
 * bao lâu).
 */
public record CreditBatchView(
        UUID id,
        String packageCode,
        int initialAmount,
        int remainingAmount,
        Instant activatedAt,
        Instant expiresAt,
        CreditBatchStatus status
) {

    public CreditBatchView {
        if (id == null || packageCode == null || activatedAt == null || expiresAt == null) {
            throw new IllegalArgumentException("creditBatchView thiếu trường bắt buộc");
        }
    }
}
