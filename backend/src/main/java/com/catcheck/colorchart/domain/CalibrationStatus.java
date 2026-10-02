package com.catcheck.colorchart.domain;

/**
 * Trạng thái job hiệu chuẩn bảng màu (p4 D10 {@code status}, §4.4.3 VARCHAR + CHECK).
 */
public enum CalibrationStatus {
    QUEUED,
    RUNNING,
    REVIEW,
    PUBLISHED,
    FAILED
}
