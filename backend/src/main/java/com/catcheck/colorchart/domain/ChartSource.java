package com.catcheck.colorchart.domain;

/**
 * Nguồn gốc dữ liệu bảng màu (p4 D5 {@code source}, §4.4.3 VARCHAR + CHECK).
 *
 * <p>{@code MANUAL_HEX} là luồng 1 (admin nhập hex, server tính Lab) — dùng cho seed placeholder.
 * {@code CALIBRATED}/{@code SPECTRO} là luồng 2 (hiệu chu�n từ ảnh mẫu / spectrophotometer) —
 * bắt buộc trước go-live thật (p6 §6.6.3).
 */
public enum ChartSource {
    MANUAL_HEX,
    CALIBRATED,
    SPECTRO
}
