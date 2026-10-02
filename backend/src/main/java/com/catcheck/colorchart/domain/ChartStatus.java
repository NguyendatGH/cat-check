package com.catcheck.colorchart.domain;

/**
 * Trạng thái phiên bản bảng màu (p4 D5 {@code status}, §4.4.3 VARCHAR + CHECK).
 *
 * <p>Không bao giờ sửa bản {@code ACTIVE} — publish = tạo version mới + archive bản cũ
 * (p6 §6.6.5). Đây là điều kiện để {@code scan_analysis.chart_version} có ý nghĩa.
 */
public enum ChartStatus {
    DRAFT,
    ACTIVE,
    ARCHIVED
}
