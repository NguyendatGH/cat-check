package com.catcheck.notification.domain;

/**
 * {@code notification.ref_type} (p4 F2).
 *
 * <p>CHECK của V13 chỉ nhận 5 giá trị dưới đây. p4 §4.4.7 có liệt kê thêm {@code DSAR} nhưng
 * {@code ck_notification_ref_type} trong {@code V13__notification.sql} KHÔNG có — DDL là thứ
 * chạy thật nên enum Java bám DDL. Thêm {@code DSAR} sẽ làm INSERT ném
 * {@code CheckViolationException} lúc chạy.</p>
 */
public enum NotificationRefType {
    REMINDER,
    HEALTH_FLAG,
    CREDIT,
    EXPORT,
    SYSTEM
}
