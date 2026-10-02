package com.catcheck.scan.domain;

/**
 * Phân loại kết quả — cột {@code scan_analysis.classification} (p4 D3, p6 §6.7.1).
 *
 * <p>Trùng đúng {@link com.catcheck.scan.domain.color.PhBandClassifier.Code} — hai enum tách
 * riêng vì cái này là cột JPA/persist được (nằm ngoài {@code scan.domain.color}, R9 không cho
 * package đó phụ thuộc {@code jakarta..}), còn cái kia là kiểu tính toán thuần Java. Ánh xạ
 * 1-1 bằng {@link #valueOf(String)} tại tầng application.
 */
public enum ScanClassification {
    IN_RANGE,
    SLIGHTLY_LOW,
    SLIGHTLY_HIGH,
    LOW,
    HIGH,
    INCONCLUSIVE
}
