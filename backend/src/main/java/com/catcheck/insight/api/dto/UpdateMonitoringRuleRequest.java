package com.catcheck.insight.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Body của L39 ({@code PATCH /admin/monitoring-rules/{code}}) — merge-patch: <b>vắng field =
 * không đổi</b> (p8 §8.1.11).
 *
 * <p>Mọi field dùng kiểu bọc, không kiểu nguyên thuỷ (handoff H15.99): với {@code record},
 * Jackson truyền {@code null} cho property vắng mặt và {@code FAIL_ON_NULL_FOR_PRIMITIVES} ném
 * ngay ở bước bind ⇒ {@code 500} thay vì {@code 400} — và ném TRƯỚC khi controller kịp kiểm
 * {@code If-Match}, nên nhánh {@code 428} cũng không quan sát được.</p>
 *
 * @param enabled       bật/tắt rule
 * @param params        tham số rule ({@code windowHours}, {@code minCount}, {@code minDelta}…);
 *                      thay TOÀN BỘ map, không merge từng khoá — merge sâu trong JSONB không có
 *                      cách nào xoá một khoá đã lỗi thời
 * @param cooldownHours cửa sổ chặn trùng; {@code 0} chỉ hợp lệ cho {@code URGENT_CLINICAL_SIGN}
 *                      (p4 D11)
 * @param pushEnabled   có gửi FCM không (quyết định #16, p4 D11)
 * @param reason        ký hiệu {@code Rsn} của p8 §8.4.12 — bắt buộc ≥ 10 ký tự
 */
public record UpdateMonitoringRuleRequest(
        Boolean enabled,
        Map<String, Object> params,
        @Min(0) Integer cooldownHours,
        Boolean pushEnabled,
        @NotBlank @Size(min = 10, max = 500) String reason
) {
}
