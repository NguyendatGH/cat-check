package com.catcheck.privacy.domain;

/**
 * Nguồn của ngày nghỉ trong {@code holiday_calendar} (p4 §4.4.3 nhóm B, p15 REQ-DSAR-02).
 *
 * <p>Chỉ {@code OFFICIAL} được dùng để tính SLA theo ngày làm việc — nghỉ bù do công ty
 * tự quyết định ({@code COMPANY}) <b>không</b> được dùng để kéo dài SLA luật định.</p>
 */
public enum HolidaySource {
    OFFICIAL,
    COMPANY
}
