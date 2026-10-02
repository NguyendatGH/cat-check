package com.catcheck.credit.domain;

import java.time.Instant;

/**
 * Trạng thái lô credit — {@code credit_batch.status} (p4 §4.4.6).
 *
 * <p>Phân biệt hai trạng thái "hết" là cố ý: {@link #EXHAUSTED} là do người dùng tiêu hết,
 * {@link #EXPIRED} là do hết hạn theo lô. Cả hai đều đưa {@code remaining_amount} về 0 nhưng
 * nghĩa nghiệp vụ khác nhau khi đối soát (p5 R3).</p>
 */
public enum CreditBatchStatus {

    /** Còn hiệu lực, có thể bị FEFO chọn để trừ. */
    ACTIVE,

    /** Đã tiêu hết trước hạn. */
    EXHAUSTED,

    /** Hết hạn, phần chưa dùng về 0 — KHÔNG xoá dòng (p5 R3). */
    EXPIRED;

    /**
     * Lô còn hiệu lực nghĩa là {@code ACTIVE} VÀ {@code expires_at > now} (bất biến I3).
     *
     * <p>Điều kiện thời gian là bắt buộc kể cả khi {@code status='ACTIVE'}: job
     * {@code ExpireCreditBatchesJob} chạy mỗi giờ nên giữa hai lần chạy luôn có lô
     * {@code status='ACTIVE'} mà {@code expires_at} đã qua (p17 C2: lô hết hạn không bị tiêu
     * "kể cả khi job hết hạn chưa chạy").</p>
     */
    public boolean isLive(Instant expiresAt, Instant now) {
        return this == ACTIVE && expiresAt.isAfter(now);
    }

    /** Trạng thái đích sau khi lô không còn credit khả dụng. */
    public static CreditBatchStatus closedBy(int remainingAmount) {
        return remainingAmount == 0 ? EXHAUSTED : ACTIVE;
    }
}
