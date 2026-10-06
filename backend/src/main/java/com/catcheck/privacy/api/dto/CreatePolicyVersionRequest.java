package com.catcheck.privacy.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Body của L47 ({@code POST /admin/policy-versions}) — soạn bản mới.
 *
 * <p>Mọi field dùng kiểu bọc, không kiểu nguyên thuỷ (handoff H15.99): với {@code record},
 * Jackson truyền {@code null} cho property vắng mặt và {@code FAIL_ON_NULL_FOR_PRIMITIVES} ném
 * ngay ở bước bind ⇒ {@code 500} thay vì {@code 400}.</p>
 *
 * @param policyType        TERMS | PRIVACY | COOKIE | MEDICAL_DISCLAIMER | … (p4 §4.4.3 nhóm B)
 * @param version           semver {@code MAJOR.MINOR.PATCH} (p15 REQ-VER-01)
 * @param locale            {@code vi} hoặc {@code en} ({@code ck_policy_version_locale})
 * @param contentMd         nội dung markdown; một trong hai cùng {@code contentUrl} phải có
 *                          ({@code ck_policy_version_source})
 * @param summaryOfChanges  "có gì thay đổi" — p15 REQ-VER-04 đòi modal xin lại đồng ý hiển thị
 *                          đúng chuỗi này thay vì bắt user đọc lại toàn văn
 * @param requiresReconsent {@code true} = chặn user tới khi đồng ý lại; MAJOR bump BẮT BUỘC
 *                          {@code true} + {@code affectedPurposes} (p15 REQ-VER-02)
 * @param affectedPurposes  các {@code consent_purpose.code} cần xin lại — quyết định xin lại
 *                          NHỮNG consent nào thay vì xin lại tất cả
 * @param effectiveFrom     ngày hiệu lực; phải ở TƯƠNG LAI, nếu không bản nháp có hiệu lực ngay
 *                          và bước publish của DPO bị vòng qua
 * @param reason            ký hiệu {@code Rsn} của p8 §8.4.12 — bắt buộc ≥ 10 ký tự
 */
public record CreatePolicyVersionRequest(
        @NotBlank String policyType,

        @NotBlank
        @Pattern(regexp = "^\\d{1,4}\\.\\d{1,4}(\\.\\d{1,4})?$",
                message = "version phải là semver MAJOR.MINOR[.PATCH]")
        String version,

        @Pattern(regexp = "^(vi|en)$", message = "locale chỉ nhận vi hoặc en") String locale,

        @NotBlank @Size(max = 300) String title,

        String contentMd,

        String contentUrl,

        @Size(max = 4000) String summaryOfChanges,

        @NotNull Boolean requiresReconsent,

        List<String> affectedPurposes,

        @NotNull Instant effectiveFrom,

        @NotBlank @Size(min = 10, max = 500) String reason
) {

    /** {@code vi} là bản CÓ HIỆU LỰC PHÁP LÝ khi thiếu bản dịch (p15 REQ-LEGAL-07). */
    public String localeOrDefault() {
        return locale == null || locale.isBlank() ? "vi" : locale;
    }
}
