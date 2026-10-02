package com.catcheck.credit.api.dto;

import java.time.Instant;

/**
 * {@code POST /api/v1/activations} (p8 H1).
 *
 * @param packageCode    gói vừa kích hoạt
 * @param packageName    tên hiển thị
 * @param creditsGranted số credit được cấp
 * @param expiresAt      hạn credit, tính từ lúc kích hoạt
 * @param balanceAfter   số dư khả dụng toàn user sau kích hoạt
 */
public record ActivationResponse(
        String packageCode,
        String packageName,
        int creditsGranted,
        Instant expiresAt,
        int balanceAfter
) {
}
