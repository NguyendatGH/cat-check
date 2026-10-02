package com.catcheck.credit.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Số dư credit hiển thị cho user: tổng khả dụng + chi tiết từng lô (p5 R4).
 *
 * <p>p5 R4 nêu rõ đây là yêu cầu trực tiếp từ owner: <i>"vẫn có các mốc thời gian để tính
 * toán lượng available time for each group credit"</i>. Vì vậy response phải có cả tổng lẫn
 * từng lô kèm đếm ngược, không được chỉ trả một con số.</p>
 */
public record CreditBalance(
        int availableBalance,
        int trialScansUsed,
        int trialScansRemaining,
        List<BatchAvailability> batches
) {

    public CreditBalance {
        if (batches == null) {
            batches = List.of();
        }
    }

    /**
     * Một lô credit còn hiệu lực kèm số còn lại và thời gian còn lại.
     *
     * @param batchId         id lô
     * @param packageCode     mã gói đã snapshot
     * @param initialAmount   số credit ban đầu
     * @param remainingAmount số credit còn lại
     * @param activatedAt     lúc kích hoạt
     * @param expiresAt       mốc hết hạn credit của riêng lô này
     * @param remaining       thời gian còn lại tới {@code expiresAt}, null nếu đã hết hạn
     */
    public record BatchAvailability(
            UUID batchId,
            String packageCode,
            int initialAmount,
            int remainingAmount,
            Instant activatedAt,
            Instant expiresAt,
            Duration remaining
    ) {

        public BatchAvailability {
            if (remainingAmount < 0 || remainingAmount > initialAmount) {
                throw new IllegalArgumentException(
                        "remainingAmount phải nằm trong [0, initialAmount]: " + batchId);
            }
        }
    }
}
