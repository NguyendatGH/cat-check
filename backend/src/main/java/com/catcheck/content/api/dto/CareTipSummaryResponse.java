package com.catcheck.content.api.dto;

import com.catcheck.content.domain.CareTip;
import com.catcheck.content.domain.CareTipCategory;
import com.catcheck.content.domain.CareTipKind;

import java.util.List;

/**
 * Bài viết ở dạng rút gọn cho danh sách F7.
 *
 * <p>Không có {@code bodyMd}: danh sách công khai có thể trả hàng chục bài, kéo theo toàn bộ
 * markdown vào mỗi response là chi phí vô nghĩa. Client chỉ cần tiêu đề để dựng card.</p>
 *
 * @param slug      dùng làm path param của F8 (ngoại lệ duy nhất của p8 §8.1.3 vì SEO/deep link)
 * @param summary   tóm tắt, có thể null
 * @param publishedAt thời điểm công bố, ISO-8601 UTC với hậu tố Z
 */
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
