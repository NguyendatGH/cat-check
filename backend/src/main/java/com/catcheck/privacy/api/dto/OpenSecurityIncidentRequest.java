package com.catcheck.privacy.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Body L60 — mở hồ sơ sự cố.
 *
 * <p>Không có field nào cho {@code retainUntil}: REQ-INC-01 chốt nó bằng
 * {@code resolved_at + 5 năm} và đó là thời hạn pháp lý (Đ29.1.c NĐ356).</p>
 *
 * <p>Mọi field là kiểu bọc (H15.99): {@code record} + Jackson truyền {@code null} cho
 * property vắng mặt, nên một {@code int} ở {@code affectedSubjectCount} sẽ biến request
 * thiếu field thành {@code 500}.</p>
 *
 * @param severity   LOW/MEDIUM/HIGH/CRITICAL (p4 §4.4.3 nhóm B)
 * @param category   DATA_BREACH/DATA_LOSS/UNAUTHORIZED_ACCESS/AVAILABILITY/OTHER
 * @param detectedAt mốc PHÁT HIỆN, khởi động đồng hồ 72 giờ; vắng ⇒ lấy {@code now()}
 * @param reason     ký hiệu {@code Rsn} — bắt buộc ≥ 10 ký tự
 */
public record OpenSecurityIncidentRequest(
        @NotBlank String severity,
        @NotBlank String category,
        @NotBlank @Size(max = 4000) String summary,
        @PositiveOrZero Integer affectedSubjectCount,
        List<@Size(max = 8) String> affectedDataCodes,
        Instant detectedAt,
        @Size(max = 500) String reportRef,
        @NotNull String reason) {
}
