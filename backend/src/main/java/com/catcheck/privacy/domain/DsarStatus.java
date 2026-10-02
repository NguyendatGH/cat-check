package com.catcheck.privacy.domain;

/**
 * Trạng thái xử lý {@code dsar_request} (p4 §4.4.3 nhóm B). Đồng hồ SLA bắt đầu ở
 * {@code received_at} và không dịch chuyển khi {@code holiday_calendar} được cập
 * nhật sau (p4 B5).
 */
public enum DsarStatus {
    /** Đã nhận, đồng hồ SLA chạy. */
    RECEIVED,
    /** Chờ xác minh danh tính (p15 REQ-DSAR-04) — bắt buộc trước IN_PROGRESS với ACCESS_EXPORT/ERASE. */
    IDENTITY_PENDING,
    /** Đang xử lý. */
    IN_PROGRESS,
    /** Đã gia hạn một lần, bắt buộc có lý do. */
    EXTENDED,
    /** Hoàn tất. */
    COMPLETED,
    /** Từ chối — bắt buộc nêu lý do chính đáng (Đ13.3/Đ14.5). */
    REJECTED
}
