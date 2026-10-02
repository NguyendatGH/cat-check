package com.catcheck.privacy.domain;

/**
 * Mức độ sự cố lộ/mất dữ liệu (p4 §4.4.3 nhóm B, p15 §15.9.5).
 *
 * <p>Từ {@code HIGH} trở lên bắt buộc thông báo cơ quan quản lý trong 72 giờ kể từ
 * {@code detected_at}.</p>
 */
public enum IncidentSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
