package com.catcheck.privacy.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Body L61 — phân loại lại, ghi mốc thông báo 72 giờ và kết luận. Merge-patch: field vắng
 * mặt = không đổi (p8 §8.1.11).
 *
 * <p>Không nhận {@code detectedAt}: đổi mốc phát hiện là đổi hạn 72 giờ sau khi hạn đó đã
 * bắt đầu chạy. Không nhận {@code retainUntil}: service tính từ {@code resolvedAt}
 * (REQ-INC-01).</p>
 *
 * @param reason ký hiệu {@code Rsn} — bắt buộc ≥ 10 ký tự
 */
public record UpdateSecurityIncidentRequest(
        String severity,
        String category,
        @Size(max = 4000) String summary,
        @PositiveOrZero Integer affectedSubjectCount,
        List<@Size(max = 8) String> affectedDataCodes,
        Instant classifiedAt,
        Instant containedAt,
        Instant authorityNotifiedAt,
        Instant subjectsNotifiedAt,
        Instant resolvedAt,
        @Size(max = 500) String reportRef,
        @NotNull String reason) {
}
