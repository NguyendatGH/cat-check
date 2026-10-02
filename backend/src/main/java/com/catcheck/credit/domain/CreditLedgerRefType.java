package com.catcheck.credit.domain;

/**
 * Loại tài nguyên mà một dòng {@code credit_ledger} tham chiếu — {@code credit_ledger.ref_type}
 * (p4 §4.4.6).
 *
 * <p>{@link #refId} cố ý KHÔNG phải foreign key: nó đa hình theo {@code ref_type}
 * (scan / activation / job / admin) nên không có bảng đích cố định (p4 §4.9.3, vòng lặp 2).
 * Đổi lại mất ràng buộc tham chiếu, bù bằng index partial {@code (ref_type, ref_id)} và test.</p>
 *
 * <p><b>Khiếm khuyết hợp đồng đã biết (chưa sửa):</b> type này là tham số của
 * {@code credit.api.CreditConsumption.CreditConsumeCommand} nên về hợp đồng nó thuộc bề mặt công
 * khai "api", nhưng KHÔNG THỂ đánh dấu {@code @NamedInterface} thẳng ở đây — R8 (ArchUnit) cấm mọi
 * annotation {@code org.springframework..} trong {@code ..domain..}, không có ngoại lệ cho
 * annotation thuần metadata như {@code NamedInterface}. Dời enum sang {@code credit.api} thì vi
 * phạm R1 ngược lại vì {@code credit.domain.LedgerEntry} (domain) đang dùng type này. Cách sửa
 * đúng là tách type API riêng ở {@code credit.api} + converter ở tầng application — CHƯA làm vì
 * đụng vào hợp đồng {@code CreditConsumption} mà {@code scan} (A6) đã tiêu thụ. Ghi nhận trong
 * {@code ModularityTests} là vi phạm đã biết, xem docs/handovers.</p>
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
