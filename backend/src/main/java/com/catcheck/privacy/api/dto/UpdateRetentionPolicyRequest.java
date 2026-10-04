package com.catcheck.privacy.api.dto;

import com.catcheck.privacy.domain.RetentionAction;

/**
 * Payload cập nhật cấu hình retention. Các trường nullable được hiểu là giữ giá trị hiện tại;
 * frontend gửi toàn bộ giá trị đang hiển thị để thao tác PATCH rõ ràng và idempotent.
 */
public record UpdateRetentionPolicyRequest(
        String dataInventoryCode,
        String targetTable,
        Integer retentionDays,
        String anchorColumn,
        RetentionAction actionOnExpiry,
        String jobName,
        Integer safetyThresholdPercent,
        Boolean enabled,
        String legalBasis
) {
}
