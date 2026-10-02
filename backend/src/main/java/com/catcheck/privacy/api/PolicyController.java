package com.catcheck.privacy.api;

import com.catcheck.privacy.api.dto.AcknowledgePolicyRequest;
import com.catcheck.privacy.api.dto.PolicyVersionListResponse;
import com.catcheck.privacy.api.dto.PolicyVersionSummaryView;
import com.catcheck.privacy.api.dto.PolicyView;
import com.catcheck.privacy.application.PolicyService;
import com.catcheck.privacy.application.RequestEvidence;
import com.catcheck.privacy.domain.PolicySurface;
import com.catcheck.privacy.domain.PolicyType;
import com.catcheck.privacy.domain.PolicyVersion;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.security.CurrentUser;
import com.catcheck.shared.security.SecurityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * C16–C17 — văn bản pháp lý công khai + ghi nhận "đã đọc" (p8 §8.4.3).
 *
 * <p>Path là {@code /api/v1/policies} (không có tiền tố {@code /privacy}) — đúng bảng
 * endpoint của p8. C16 công khai; C17 cần đăng nhập. Controller không chạm type
 * {@code ..domain..} (ArchUnit R4) — chuyển đổi domain↔DTO nằm ở service/mapper.</p>
 */
@RestController
@RequestMapping("/api/v1/policies")
@Tag(name = "Policies", description = "Văn bản pháp lý công khai và ghi nhận đã đọc")
public class PolicyController {

    private final PolicyService policyService;

    public PolicyController(PolicyService policyService) {
        this.policyService = policyService;
    }

    /**
     * C16 — bản hiện hành của TERMS|PRIVACY|COOKIE|MEDICAL_DISCLAIMER (công khai).
     * Rate limit 300/phút do lớp Bucket4j của W3 ép (p8 §8.3.5).
     */
    @Operation(
            operationId = "getCurrentPolicy",
            summary = "Bản hiện hành của một tài liệu pháp lý",
            description = """
                    Bản đang hiệu lực của TERMS|PRIVACY|COOKIE|MEDICAL_DISCLAIMER, công khai, \
                    không cần đăng nhập. Không tìm thấy bản hiệu lực ⇒ 404 POLICY_VERSION_NOT_FOUND.""")
    @GetMapping("/{policyCode}")
    public ResponseEntity<PolicyView> getCurrentPolicy(
            @PathVariable("policyCode") String policyCode,
            @RequestParam(name = "locale", required = false) String locale
    ) {
        PolicyType type = policyService.parsePublicType(policyCode);
        String resolvedLocale = locale != null && locale.equals("en") ? "en" : "vi";
        PolicyVersion policy = policyService.current(type, resolvedLocale)
                .orElseThrow(() -> new BusinessRuleException(
                        PrivacyErrorCode.POLICY_VERSION_NOT_FOUND, policyCode));
        return ResponseEntity.ok(new PolicyView(
                policy.policyType().name(),
                policy.version(),
                policy.locale(),
                policy.title(),
                policy.contentMd(),
                policy.contentUrl(),
                policy.effectiveFrom(),
                policy.requiresReconsent(),
                policy.summaryOfChanges(),
                policy.affectedPurposes()));
    }

    /**
     * F9 — danh sách phiên bản của một tài liệu (p15 REQ-LEGAL-02).
     *
     * <p>Công khai, trả CẢ bản đã hết hiệu lực: màn "Xem các phiên bản trước" tồn tại để
     * chứng minh user đã đồng ý với nội dung nào. Không kèm nội dung markdown — xem F10.</p>
     */
    @Operation(
            operationId = "listPolicyVersions",
            summary = "Danh sách phiên bản của một tài liệu pháp lý",
            description = """
                    Mọi phiên bản (kể cả đã hết hiệu lực) của TERMS|PRIVACY|COOKIE|\
                    MEDICAL_DISCLAIMER, mới nhất trước. Công khai. Chưa có bản dịch cho \
                    locale yêu cầu thì lùi về 'vi' (REQ-LEGAL-07).""")
    @GetMapping("/{policyCode}/versions")
    public ResponseEntity<PolicyVersionListResponse> listPolicyVersions(
            @PathVariable("policyCode") String policyCode,
            @RequestParam(name = "locale", required = false) String locale
    ) {
        PolicyType type = policyService.parsePublicType(policyCode);
        String resolvedLocale = "en".equals(locale) ? "en" : PolicyService.DEFAULT_LOCALE;
        UUID currentId = policyService.current(type, resolvedLocale)
                .map(PolicyVersion::id)
                .orElse(null);
        List<PolicyVersionSummaryView> items = policyService.allVersions(type, resolvedLocale).stream()
                .map(v -> new PolicyVersionSummaryView(
                        v.version(),
                        v.locale(),
                        v.title(),
                        v.effectiveFrom(),
                        v.effectiveTo(),
                        v.id().equals(currentId),
                        v.requiresReconsent(),
                        v.summaryOfChanges(),
                        v.affectedPurposes()))
                .toList();
        return ResponseEntity.ok(new PolicyVersionListResponse(type.name(), items));
    }

    /**
     * F10 — permalink một phiên bản cụ thể (p15 REQ-LEGAL-03).
     *
     * <p><b>URL này phải sống vĩnh viễn</b>: {@code consent_record.policy_version} trỏ thẳng
     * vào đây làm bằng chứng pháp lý. Vì vậy KHÔNG lọc theo thời gian hiệu lực (bản cũ vẫn
     * mở được) và không bao giờ trả 410 — chỉ 404 khi phiên bản chưa từng tồn tại. Nội dung
     * đã publish là bất biến (REQ-VER-07) nên đặt {@code Cache-Control: public} dài hạn.</p>
     */
    @Operation(
            operationId = "getPolicyVersionPermalink",
            summary = "Permalink một phiên bản chính sách",
            description = """
                    Nội dung đầy đủ của MỘT phiên bản theo semver. Công khai và phải truy cập \
                    được vĩnh viễn kể cả khi đã bị thay thế (REQ-LEGAL-03) — consent_record \
                    trỏ tới URL này. Không tồn tại ⇒ 404 POLICY_VERSION_NOT_FOUND.""")
    @GetMapping("/{policyCode}/versions/{version}")
    public ResponseEntity<PolicyView> getPolicyVersionPermalink(
            @PathVariable("policyCode") String policyCode,
            @PathVariable("version") String version,
            @RequestParam(name = "locale", required = false) String locale
    ) {
        PolicyType type = policyService.parsePublicType(policyCode);
        String resolvedLocale = "en".equals(locale) ? "en" : PolicyService.DEFAULT_LOCALE;
        PolicyVersion policy = policyService.versionPermalink(type, version, resolvedLocale)
                .orElseThrow(() -> new BusinessRuleException(
                        PrivacyErrorCode.POLICY_VERSION_NOT_FOUND, policyCode + "/" + version));
        return ResponseEntity.ok()
                // Bản đã publish là bất biến (REQ-VER-07) nên cache được lâu; p8 §8.3 mặc định
                // `private, no-store` chỉ áp cho dữ liệu user, đây là văn bản công khai.
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(new PolicyView(
                        policy.policyType().name(),
                        policy.version(),
                        policy.locale(),
                        policy.title(),
                        policy.contentMd(),
                        policy.contentUrl(),
                        policy.effectiveFrom(),
                        policy.requiresReconsent(),
                        policy.summaryOfChanges(),
                        policy.affectedPurposes()));
    }

    /**
     * C17 — ghi {@code policy_acknowledgment} (disclaimer onboarding — KHÔNG phải
     * consent). Idempotent: một bản chỉ ghi một lần cho một điểm chạm (p4 B4).
     */
    @Operation(
            operationId = "acknowledgePolicy",
            summary = "Ghi nhận đã đọc văn bản pháp lý",
            description = """
                    Ghi policy_acknowledgment cho bản hiện hành của tài liệu — đây là bằng \
                    chứng ĐÃ THÔNG BÁO (disclaimer y tế, Điều khoản), KHÔNG phải consent dữ \
                    liệu cá nhân (p4 B4). Idempotent theo UNIQUE (user, version, surface).""")
    @PostMapping("/{policyCode}/acknowledge")
    public ResponseEntity<Void> acknowledgePolicy(
            @CurrentUser SecurityPrincipal user,
            @PathVariable("policyCode") String policyCode,
            @Valid @RequestBody AcknowledgePolicyRequest request,
            HttpServletRequest httpRequest
    ) {
        PolicyType type = policyService.parsePublicType(policyCode);
        PolicySurface surface;
        try {
            surface = PolicySurface.valueOf(request.surface());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException(PrivacyErrorCode.VALIDATION_FAILED, request.surface());
        }
        policyService.acknowledge(
                user.userId(),
                type,
                "vi",
                surface,
                RequestEvidence.of(
                        httpRequest.getHeader("X-Request-Id"),
                        httpRequest.getRemoteAddr(),
                        httpRequest.getHeader("User-Agent")));
        return ResponseEntity.noContent().build();
    }
}
