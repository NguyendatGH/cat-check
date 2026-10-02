package com.catcheck.scan.domain;

/**
 * Trạng thái một lần quét — cột {@code scan.status} (p4 D1).
 *
 * <p>{@code FAILED} chỉ dành cho lỗi hệ thống (exception pipeline, timeout), phân biệt bằng
 * {@code failure_code}. {@code INCONCLUSIVE} (ảnh không đạt chất lượng) KHÔNG phải lỗi — nó là
 * {@code ANALYZED} với {@code scan_analysis.classification = INCONCLUSIVE} (p4 D1 ghi chú
 * nghiệp vụ, p6 §6.4.4, p8 §8.2.4(e)).
 */
public enum ScanStatus {
    PENDING,
    ANALYZED,
    FAILED,
    DISCARDED
}
