package com.catcheck.privacy.api.dto;

import java.util.List;

/**
 * F9 — envelope không phân trang cho danh sách phiên bản của MỘT tài liệu pháp lý.
 *
 * <p>Không phân trang có chủ đích: số phiên bản của một chính sách là hữu hạn và nhỏ
 * (semver bump khi luật sư cập nhật), cùng lý do với F2 {@code CatBreedListResponse}.</p>
 *
 * @param policyCode TERMS/PRIVACY/COOKIE/MEDICAL_DISCLAIMER — lặp lại để client khỏi tự suy từ URL
 * @param items      mới nhất trước (theo {@code effective_from} giảm dần)
 */
public record PolicyVersionListResponse(
        String policyCode,
        List<PolicyVersionSummaryView> items
) {
}
