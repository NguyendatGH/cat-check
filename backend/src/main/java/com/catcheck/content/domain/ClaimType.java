package com.catcheck.content.domain;

/**
 * Mức độ tuyên bố của nội dung (p4 §4.4.8).
 *
 * <p>Không có giá trị mặc định ở cấp DB ({@code ck_care_tip_claim_type} không kèm DEFAULT): người
 * soạn buộc phải chọn, vì đặt mặc định sẽ khiến một bài có tuyên bố y khoá đi qua mà không ai chú
 * ý. Mọi giá trị khác {@code NONE} đều bắt buộc có {@code source_reference} (bất biến I31 — ràng
 * buộc trùng lặp được khai ở V16 theo p4 §4.9.2; bản ứng dụng kiểm lại ở
 * {@code CareTipAdminService#publish} và trả {@code CONTENT_SOURCE_REQUIRED}).</p>
 */
public enum ClaimType {

    /** Bài chỉ nói về thao tác, không đưa ra tuyên bố hay số liệu sức khoẻ nào. */
    NONE,

    /** Có tuyên bố liên quan sức khoẻ — cần nguồn, và không được hứa hẹn chẩn đoán hay chữa bệnh. */
    MEDICAL,

    /** Có con số hoặc tỉ lệ. */
    STATISTIC,

    /** Có chứng nhận, giải phép, chứng nhận hiệp chuẩn. */
    CERTIFICATION,

    /** Có tuyên bố về hiệu năng sản phẩm hoặc dịch vụ. */
    PERFORMANCE,

    /** Có cam kết về dịch vụ (SLA, thời gian phản hồi…). */
    SERVICE_COMMITMENT;

    /** Có cần {@code source_reference} không — đây là biểu thức của bất biến I31. */
    public boolean requiresSourceReference() {
        return this != NONE;
    }
}
