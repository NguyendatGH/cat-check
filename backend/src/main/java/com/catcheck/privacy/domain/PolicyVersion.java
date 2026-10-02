package com.catcheck.privacy.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Một phiên bản văn bản chính sách — dòng {@code policy_version} (p4 B1).
 *
 * <p><b>Không bao giờ sửa</b> một phiên bản đã có {@code consent_record} trỏ tới: sửa nội
 * dung = tạo version mới. {@code content_hash} là SHA-256 của nội dung lúc publish — cốt
 * lõi của nghĩa vụ chứng minh Điều 6.2 NĐ356: phải chứng minh user đã đồng ý với
 * <b>chính xác nội dung nào</b>.</p>
 *
 * @param id                UUID v7 sinh ở tầng ứng dụng (p4 §4.1.1)
 * @param policyType       loại tài liệu (TERMS/PRIVACY/COOKIE/MEDICAL_DISCLAIMER…)
 * @param version           semver MAJOR.MINOR.PATCH (p15 REQ-VER-01)
 * @param locale            'vi' hoặc 'en' — một version có thể có nhiều bản dịch
 * @param title             tiêu đề văn bản
 * @param contentMd         nội dung markdown; null khi host ngoài (contentUrl)
 * @param contentUrl        nơi host ngoài; null khi lưu contentMd
 * @param contentHash       SHA-256 của nội dung lúc publish — bằng chứng bất biến
 * @param summaryOfChanges  tóm tắt "có gì đổi" hiển thị khi xin lại đồng ý (changelog_vi)
 * @param requiresReconsent true = chặn user cho tới khi đồng ý lại (p15 REQ-VER-02)
 * @param affectedPurposes  các consent_purpose.code bị ảnh hưởng — xin lại NHỮNG consent đó
 * @param effectiveFrom    mốc bắt đầu hiệu lực
 * @param effectiveTo      null = đang hiệu lực
 * @param publishedBy      admin/DPO phát hành, null khi seed
 * @param createdAt         mốc tạo bản ghi
 */
public record PolicyVersion(
        UUID id,
        PolicyType policyType,
        String version,
        String locale,
        String title,
        String contentMd,
        String contentUrl,
        String contentHash,
        String summaryOfChanges,
        boolean requiresReconsent,
        List<String> affectedPurposes,
        Instant effectiveFrom,
        Instant effectiveTo,
        UUID publishedBy,
        Instant createdAt
) {

    public PolicyVersion {
        if (id == null || policyType == null || version == null || locale == null) {
            throw new IllegalArgumentException("policyVersion thiếu trường bắt buộc");
        }
        if (contentMd == null && contentUrl == null) {
            throw new IllegalArgumentException(
                    "policyVersion phải có content_md hoặc content_url (ck_policy_version_source)");
        }
        if (contentHash == null || contentHash.length() != 64) {
            throw new IllegalArgumentException("policyVersion.contentHash phải là SHA-256 (64 ký tự hex)");
        }
        if (effectiveFrom == null) {
            throw new IllegalArgumentException("policyVersion.effectiveFrom bắt buộc");
        }
        affectedPurposes = affectedPurposes == null ? List.of() : List.copyOf(affectedPurposes);
    }

    /** Đang hiệu lực tại {@code now} (effective_from <= now và effective_to chưa tới). */
    public boolean isEffectiveAt(Instant now) {
        return !effectiveFrom.isAfter(now)
                && (effectiveTo == null || effectiveTo.isAfter(now));
    }
}
