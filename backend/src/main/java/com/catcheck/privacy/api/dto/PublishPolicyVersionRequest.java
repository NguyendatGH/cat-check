package com.catcheck.privacy.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Body của L48 ({@code POST /admin/policy-versions/{id}/publish}).
 *
 * @param effectiveFrom ghi đè ngày hiệu lực của bản nháp; {@code null} = giữ nguyên. Cho ghi đè
 *                      vì khoảng thời gian từ lúc soạn tới lúc rà soát pháp lý xong là không
 *                      đoán được, và p15 REQ-VER-06 tính mốc "báo trước ≥ 7 ngày" từ ngày này
 * @param reason        ký hiệu {@code Rsn} của p8 §8.4.12 — bắt buộc ≥ 10 ký tự
 */
public record PublishPolicyVersionRequest(
        Instant effectiveFrom,
        @NotBlank @Size(min = 10, max = 500) String reason
) {
}
