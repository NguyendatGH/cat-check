package com.catcheck.content.api.dto;

import com.catcheck.content.domain.CareTip;
import com.catcheck.content.domain.CareTipCategory;
import com.catcheck.content.domain.CareTipKind;
import com.catcheck.content.domain.CareTipStatus;
import com.catcheck.content.domain.ClaimType;

import java.util.List;

/**
 * Bài viết ở dạng đầy đủ cho F8 và cho màn quản trị.
 *
 * <p>Có thêm {@code status} và {@code reviewedAt} so với bản công khai. Ở endpoint công khai các
 * trường này luôn là {@code PUBLISHED} và có giá trị, nên trả kèm không làm lộ thêm thông tin gì —
 * nhưng tài liệu OpenAPI chỉ cần khai một shape.</p>
 */
public record CareTipDetailResponse(
        String id,
        String slug,
        String translationGroupId,
        String locale,
        CareTipKind kind,
        CareTipCategory category,
        String title,
        String summary,
        String bodyMd,
        String coverImageUrl,
        List<String> tags,
        CareTipStatus status,
        ClaimType claimType,
        String sourceReference,
        Integer sortWeight,
        String authorId,
        String reviewedBy,
        String reviewedAt,
        String publishedAt,
        String createdAt,
        String updatedAt
) {

    public static CareTipDetailResponse from(CareTip tip) {
        return new CareTipDetailResponse(
                tip.getId().toString(),
                tip.getSlug(),
                tip.getTranslationGroup().toString(),
                tip.getLocale(),
                tip.getKind(),
                tip.getCategory(),
                tip.getTitle(),
                tip.getSummary(),
                tip.getBodyMd(),
                tip.getCoverImageUrl(),
                tip.getTags(),
                tip.getStatus(),
                tip.getClaimType(),
                tip.getSourceReference(),
                tip.getSortWeight(),
                tip.getAuthorId() == null ? null : tip.getAuthorId().toString(),
                tip.getReviewedBy() == null ? null : tip.getReviewedBy().toString(),
                tip.getReviewedAt() == null ? null : tip.getReviewedAt().toString(),
                tip.getPublishedAt() == null ? null : tip.getPublishedAt().toString(),
                tip.getCreatedAt().toString(),
                tip.getUpdatedAt().toString());
    }
}
