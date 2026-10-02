package com.catcheck.privacy.api.dto;

import java.time.Instant;

/**
 * Một dòng bằng chứng consent — response C4 (lịch sử tuân thủ).
 *
 * @param purposeCode    mã mục đích
 * @param status         GRANTED/DENIED/WITHDRAWN
 * @param policyVersion  phiên bản chính sách đang hiệu lực lúc ghi
 * @param policyHash     SHA-256 nội dung lúc ghi — chứng minh "đã đồng ý với nội dung nào"
 * @param method         kênh ghi nhận
 * @param uiSurface      màn hình xuất phát (register, privacy_center…)
 * @param occurredAt     thời điểm hành động (UTC)
 */
public record ConsentHistoryView(
        String purposeCode,
        String status,
        String policyVersion,
        String policyHash,
        String method,
        String uiSurface,
        Instant occurredAt
) {
}
