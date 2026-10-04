package com.catcheck.credit.domain;

/**
 * Loại tài nguyên mà một dòng {@code credit_ledger} tham chiếu — {@code credit_ledger.ref_type}
 * (p4 §4.4.6).
 *
 * <p>{@link #refId} cố ý KHÔNG phải foreign key: nó đa hình theo {@code ref_type}
 * (scan / activation / job / admin) nên không có bảng đích cố định (p4 §4.9.3, vòng lặp 2).
 * Đổi lại mất ràng buộc tham chiếu, bù bằng index partial {@code (ref_type, ref_id)} và test.</p>
 *
 * <p>This enum stays internal. Cross-module callers use
 * {@code CreditConsumption.ReferenceType}, mapped by the application service.</p>
 */
public enum CreditLedgerRefType {

    /** {@code ref_id} = {@code scan.id} — trừ khi lưu scan, hoặc hoàn khi pipeline lỗi (p5 R7). */
    SCAN,

    /** {@code ref_id} = {@code activation_code.id} — cấp credit lúc kích hoạt mã. */
    ACTIVATION,

    /** {@code ref_id} = {@code job_run.id} — ghi {@code EXPIRE} bởi job nền. */
    JOB,

    /** {@code ref_id} = {@code app_user.id} của admin thao tác — kèm audit (M6). */
    ADMIN
}
