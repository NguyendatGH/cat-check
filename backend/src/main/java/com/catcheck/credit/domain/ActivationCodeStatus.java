package com.catcheck.credit.domain;

/**
 * Trạng thái mã kích hoạt — {@code activation_code.status}.
 *
 * <p>Giá trị trên dây/DB giống hệt từng ký tự (p4 §4.1.3 {@code VARCHAR + CHECK}, p4 §4.4.6).</p>
 *
 * @see com.catcheck.credit.domain.CreditBatchStatus
 */
public enum ActivationCodeStatus {

    /** Đã phát hành, chưa dùng. */
    ISSUED,

    /** Đã đổi lấy credit batch, không dùng lại được (p5 R1: một mã chỉ dùng một lần). */
    REDEEMED,

    /** Admin vô hiệu hoá (in hỏng, thu hồi lô sản xuất). */
    VOID
}
