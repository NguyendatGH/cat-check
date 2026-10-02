package com.catcheck.privacy.domain;

/**
 * Phân loại sự cố (p4 §4.4.3 nhóm B, p15 §15.9.5).
 */
public enum IncidentCategory {
    /** Lộ dữ liệu. */
    DATA_BREACH,
    /** Mất dữ liệu. */
    DATA_LOSS,
    /** Truy công trái phép. */
    UNAUTHORIZED_ACCESS,
    /** Gián đoạn dịch vụ. */
    AVAILABILITY,
    /** Khác. */
    OTHER
}
