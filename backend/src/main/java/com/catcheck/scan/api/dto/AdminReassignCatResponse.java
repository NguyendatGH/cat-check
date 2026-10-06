package com.catcheck.scan.api.dto;

/**
 * {@code POST /api/v1/admin/scans/{scanId}/reassign-cat} (p8 L18).
 *
 * <p>Không trả {@code reassignRemaining} như E9: đường admin không áp trần 3 lần (xem
 * {@code AdminScanService#reassign} và handoff H15.152), nên một con số "còn lại" sẽ là thông
 * tin sai. Trả {@code reassignCount} thật để tổng đài thấy lần quét này đã bị đổi mấy lần.</p>
 *
 * @param scanId        lần quét vừa đổi
 * @param fromCatId     mèo trước đó, {@code null} nếu trước đó không gán cho bé nào
 * @param toCatId       mèo sau khi đổi, {@code null} khi chuyển sang {@code SHARED_UNKNOWN}
 * @param toAssignment  {@code scan.assignment} sau khi đổi
 * @param reassignCount tổng số lần lần quét này đã bị đổi mèo
 */
public record AdminReassignCatResponse(
        String scanId,
        String fromCatId,
        String toCatId,
        String toAssignment,
        short reassignCount
) {
}
