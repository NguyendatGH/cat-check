package com.catcheck.privacy.api.dto;

import java.time.Instant;
import java.util.List;

/**
 * Một dòng trong danh sách phiên bản chính sách — response F9 (công khai, p8 §8.4.6).
 *
 * <p>KHÔNG kèm {@code contentMd}: danh sách chỉ để chọn bản cần xem, tải nội dung đầy đủ của
 * mọi phiên bản trong một response là lãng phí. Nội dung lấy qua permalink F10.</p>
 *
 * @param version            semver MAJOR.MINOR.PATCH — ghép vào URL permalink F10
 * @param locale             'vi'/'en'
 * @param title              tiêu đề văn bản
 * @param effectiveFrom      mốc bắt đầu hiệu lực
 * @param effectiveTo        mốc hết hiệu lực; {@code null} = đang hiệu lực
 * @param current            true nếu đây là bản đang hiệu lực tại thời điểm gọi
 * @param requiresReconsent  bản này có bắt buộc xin lại đồng ý không (p15 REQ-VER-02)
 * @param summaryOfChanges   tóm tắt "có gì đổi" (changelog_vi)
 * @param affectedPurposes   các mục đích xử lý bị ảnh hưởng
 */
public record PolicyVersionSummaryView(
        String version,
        String locale,
        String title,
        Instant effectiveFrom,
        Instant effectiveTo,
        boolean current,
        boolean requiresReconsent,
        String summaryOfChanges,
        List<String> affectedPurposes
) {
}
