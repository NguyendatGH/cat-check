package com.catcheck.privacy.api.dto;

import java.time.Instant;
import java.util.List;

/**
 * Bản hiện hành của một tài liệu pháp lý — response C16 (công khai, p8 §8.4.3).
 *
 * @param policyType        TERMS/PRIVACY/COOKIE/MEDICAL_DISCLAIMER
 * @param version            semver MAJOR.MINOR.PATCH
 * @param locale             'vi'/'en'
 * @param title              tiêu đề văn bản
 * @param contentMd          nội dung markdown — null khi host ngoài (contentUrl)
 * @param contentUrl         nơi host ngoài — null khi lưu contentMd
 * @param effectiveFrom      mốc bắt đầu hiệu lực
 * @param requiresReconsent  true = chặn user cho tới khi đồng ý lại
 * @param summaryOfChanges   tóm tắt "có gì đổi" (changelog_vi)
 * @param affectedPurposes   các mục đích bị ảnh hưởng
 */
public record PolicyView(
        String policyType,
        String version,
        String locale,
        String title,
        String contentMd,
        String contentUrl,
        Instant effectiveFrom,
        boolean requiresReconsent,
        String summaryOfChanges,
        List<String> affectedPurposes
) {
}
