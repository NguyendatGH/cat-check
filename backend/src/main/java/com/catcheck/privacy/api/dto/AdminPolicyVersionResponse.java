package com.catcheck.privacy.api.dto;

import com.catcheck.privacy.application.PolicyVersionAdminService;

import java.time.Instant;
import java.util.List;

/**
 * Một phiên bản chính sách ở màn quản trị — L46/L47/L48 (p8 §8.4.12 mục (c)).
 *
 * <p><b>Không trả {@code contentMd}.</b> Nội dung một bản chính sách là hàng chục nghìn ký tự;
 * trả nó trong MỌI dòng của danh sách biến L46 thành một response vài megabyte. Cờ
 * {@code hasContent} nói có nội dung nội bộ hay chỉ có {@code contentUrl}, còn toàn văn đọc qua
 * permalink công khai F10 ({@code GET /api/v1/policies/{code}/versions/{version}}) — cũng chính
 * là URL mà {@code consent_record} trỏ tới.</p>
 *
 * <p>{@code contentHash} thì CÓ trả: đó là bằng chứng Điều 6.2 NĐ356 (p4 B1) và là thứ DPO cần
 * đối chiếu, không phải nội dung.</p>
 *
 * @param status DRAFT | SCHEDULED | EFFECTIVE | SUPERSEDED — suy ra từ {@code published_by} +
 *               cửa sổ hiệu lực; bảng {@code policy_version} không có cột {@code status}
 */
public record AdminPolicyVersionResponse(
        String id,
        String policyType,
        String version,
        String locale,
        String title,
        Boolean hasContent,
        String contentUrl,
        String contentHash,
        String summaryOfChanges,
        Boolean requiresReconsent,
        List<String> affectedPurposes,
        Instant effectiveFrom,
        Instant effectiveTo,
        String publishedBy,
        Instant createdAt,
        String status
) {

    public static AdminPolicyVersionResponse from(PolicyVersionAdminService.AdminPolicyRow row) {
        var v = row.version();
        return new AdminPolicyVersionResponse(
                v.id().toString(),
                v.policyType().name(),
                v.version(),
                v.locale(),
                v.title(),
                row.hasContent(),
                v.contentUrl(),
                v.contentHash(),
                v.summaryOfChanges(),
                v.requiresReconsent(),
                v.affectedPurposes(),
                v.effectiveFrom(),
                v.effectiveTo(),
                v.publishedBy() == null ? null : v.publishedBy().toString(),
                v.createdAt(),
                row.status().name());
    }
}
