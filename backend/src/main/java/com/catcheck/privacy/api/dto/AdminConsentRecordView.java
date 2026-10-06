package com.catcheck.privacy.api.dto;

import com.catcheck.privacy.domain.ConsentRecord;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Một dòng {@code consent_record} cho L55 (ô {@code Q25}: <b>chỉ {@code DPO}</b>, đọc toàn bộ).
 *
 * <p>Khác {@link ConsentHistoryView} của C4 ở chỗ giữ đủ bằng chứng pháp lý mà DPO cần khi bị
 * yêu cầu chứng minh Điều 6.2 NĐ356: {@code consentTextHash} (đúng chuỗi cạnh checkbox),
 * {@code supersedesId} (dây thay thế), {@code ipAddress}/{@code userAgent}/{@code requestId}
 * (ngữ cảnh hành động) và {@code evidence}. Đó cũng là lý do p14 Q25 để ô này ❌ cho cả
 * {@code ADMIN_SUPER} — nó là dữ liệu điều tra, không phải dữ liệu hỗ trợ khách hàng.</p>
 */
public record AdminConsentRecordView(
        UUID id,
        UUID userId,
        String purposeCode,
        String status,
        UUID policyVersionId,
        String policyHash,
        String consentTextHash,
        String method,
        String uiSurface,
        String locale,
        UUID supersedesId,
        Instant occurredAt,
        String ipAddress,
        String userAgent,
        String requestId,
        Map<String, Object> evidence,
        Instant createdAt
) {

    public static AdminConsentRecordView from(ConsentRecord record) {
        return new AdminConsentRecordView(
                record.id(), record.userId(), record.purposeCode(), record.status().name(),
                record.policyVersionId(), record.policyHash(), record.consentTextHash(),
                record.method().name(), record.uiSurface(), record.locale(), record.supersedesId(),
                record.occurredAt(), record.ipAddress(), record.userAgent(), record.requestId(),
                record.evidence(), record.createdAt());
    }
}
