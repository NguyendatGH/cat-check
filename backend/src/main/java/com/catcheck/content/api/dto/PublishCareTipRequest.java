package com.catcheck.content.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body của L44 — công bố bài.
 *
 * <p>{@code reason} là bắt buộc vì L44 gắn cờ {@code Rsn} trong bảng endpoint p8 §8.4.12(d):
 * thao tác quản trị phải kèm lý do tối thiểu 10 ký tự (p15 REQ-AUD-03).</p>
 */
public record PublishCareTipRequest(
        @NotBlank
        @Size(min = 10, max = 1000, message = "reason phải dài tối thiểu 10 ký tự")
        String reason
) {
}
