package com.catcheck.credit.domain;

/**
 * Loại biến động credit — {@code credit_ledger.type} (p4 §4.4.6).
 *
 * <p>Dấu của {@code amount} bị ràng buộc ở DB bởi {@code ck_credit_ledger_sign}: dương với
 * {@link #GRANT}/{@link #REFUND}, âm với {@link #CONSUME}/{@link #EXPIRE}, tự do với
 * {@link #ADJUST}. Enum này là nguồn sự thật ở tầng ứng dụng, không phải nơi kiểm tra.</p>
 */
public enum CreditLedgerType {

    /** Cấp khi kích hoạt gói (dương). */
    GRANT,

    /** Trừ khi lưu scan (âm) — luôn đi qua FEFO. */
    CONSUME,

    /** Thu hồi phần chưa dùng của lô hết hạn (âm), bởi {@code ExpireCreditBatchesJob}. */
    EXPIRE,

    /** Hoàn khi pipeline lỗi hệ thống (dương) — p5 R7. */
    REFUND,

    /** Admin điều chỉnh (dương hoặc âm, bắt buộc {@code note} + audit). */
    ADJUST;

    /** Có bắt buộc {@code note} không — M6 (admin) chưa chốt nên hiện chưa dùng. */
    public boolean requiresNote() {
        return this == ADJUST;
    }
}
