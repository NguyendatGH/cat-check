package com.catcheck.scan.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/v1/admin/scans/{scanId}/reassign-cat} (p8 L18).
 *
 * <p>Đúng một trong {@code toCatId} / {@code toAssignment = "SHARED_UNKNOWN"} — giống hợp đồng
 * E9 của người dùng, cộng thêm {@code reason} vì L18 mang ký hiệu {@code Rsn} (p8 §8.3.2).</p>
 *
 * <p><b>Không có trường nguyên thuỷ nào</b> (H15.99): Jackson 3 truyền {@code null} cho property
 * vắng mặt và {@code FAIL_ON_NULL_FOR_PRIMITIVES} ném ở bước bind ⇒ 500 thay vì 400.</p>
 *
 * @param toCatId      id mèo đích, dạng chuỗi UUID
 * @param toAssignment {@code "SHARED_UNKNOWN"} để chuyển về "chưa rõ của bé nào"
 * @param reason       lý do, bắt buộc ≥ 10 ký tự (p15 REQ-AUD-03)
 */
public record AdminReassignCatRequest(
        String toCatId,
        String toAssignment,
        @NotBlank @Size(min = 10, max = 500) String reason
) {
}
