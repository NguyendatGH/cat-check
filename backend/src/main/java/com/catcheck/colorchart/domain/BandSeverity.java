package com.catcheck.colorchart.domain;

/**
 * Mức độ nghiêm trọng của dải phân loại pH (p4 D7 {@code severity}).
 *
 * <p>Lấy nguyên bộ của p6 §6.7.1 — đây là enum <b>khác</b> với {@code monitoring_rule.severity}
 * ({@code INFO/ATTENTION/URGENT}): một cái mô tả <em>kết quả đo nằm ở đâu</em>, một cái mô tả
 * <em>mức của cảnh báo</em>.
 */
public enum BandSeverity {
    NORMAL,
    ATTENTION,
    WATCH,
    NEUTRAL
}
