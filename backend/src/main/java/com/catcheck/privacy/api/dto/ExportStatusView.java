package com.catcheck.privacy.api.dto;

import java.time.Instant;

/**
 * Trạng thái gói xuất DSAR — response C7 (p8 §8.4.3).
 *
 * @param publicRef       mã tra cứu (DSAR-2026-000123)
 * @param status          RECEIVED/IDENTITY_PENDING/IN_PROGRESS/EXTENDED/COMPLETED/REJECTED
 * @param ackDueAt        hạn phản hồi 02 ngày làm việc
 * @param fulfilDueAt     hạn thực hiện (10 ngày với ACCESS_EXPORT)
 * @param resultExpiresAt hạn tải 72 giờ — chỉ có khi COMPLETED
 * @param completedAt     mốc hoàn tất
 */
public record ExportStatusView(
        String publicRef,
        String status,
        Instant ackDueAt,
        Instant fulfilDueAt,
        Instant resultExpiresAt,
        Instant completedAt
) {
}
