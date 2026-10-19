package com.catcheck.content.api.dto;

import com.catcheck.content.domain.CareTip;
import com.catcheck.content.domain.CareTipCategory;
import com.catcheck.content.domain.CareTipKind;

import java.util.List;

public record CareTipSummaryResponse(
        String id,
        String slug,
        String locale,
        CareTipKind kind,
        CareTipCategory category,
        String title,
        String summary,
        List<String> tags,
        String publishedAt
) {

    public static CareTipSummaryResponse from(CareTip tip) {
        return new CareTipSummaryResponse(
                tip.getId().toString(),
                tip.getSlug(),
                tip.getLocale(),
                tip.getKind(),
                tip.getCategory(),
                tip.getTitle(),
                tip.getSummary(),
                tip.getTags(),
                tip.getPublishedAt() == null ? null : tip.getPublishedAt().toString());
    }
}
