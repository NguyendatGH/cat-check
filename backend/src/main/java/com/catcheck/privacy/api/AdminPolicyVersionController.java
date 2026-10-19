package com.catcheck.privacy.api;

import com.catcheck.privacy.api.dto.AdminPolicyVersionListResponse;
import com.catcheck.privacy.api.dto.AdminPolicyVersionResponse;
import com.catcheck.privacy.api.dto.CreatePolicyVersionRequest;
import com.catcheck.privacy.api.dto.PublishPolicyVersionRequest;
import com.catcheck.privacy.application.PolicyVersionAdminService;
import com.catcheck.privacy.domain.PolicyType;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.security.AdminGuard;
import com.catcheck.shared.security.CurrentUser;
import com.catcheck.shared.security.SecurityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/policy-versions")
@Tag(name = "Quản trị phiên bản chính sách",
        description = "L46–L48 — danh sách, soạn bản mới, publish (ô Q22)")
public class AdminPolicyVersionController {

    static final Set<String> READ_ROLES = Set.of("DPO", "ADMIN_SUPER");

    static final Set<String> WRITE_ROLES = Set.of("DPO");

    private final PolicyVersionAdminService adminService;

    public AdminPolicyVersionController(PolicyVersionAdminService adminService) {
        this.adminService = adminService;
    }


    @Operation(
            operationId = "listAdminPolicyVersions",
            summary = "L46 — danh sách phiên bản chính sách",
            description = "Phân trang offset. Lọc tuỳ chọn theo `policyType` và `locale`. "
                    + "Cột `status` (DRAFT | SCHEDULED | EFFECTIVE | SUPERSEDED) được SUY RA từ "
                    + "`published_by` + cửa sổ hiệu lực — bảng `policy_version` không có cột đó. "
                    + "Response không mang `contentMd`; toàn văn đọc qua permalink F10.")
    @GetMapping
    public AdminPolicyVersionListResponse list(
            @CurrentUser SecurityPrincipal principal,
            @RequestParam(required = false) String policyType,
            @RequestParam(required = false) String locale,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        AdminGuard.requireAnyRole(principal, READ_ROLES);
        return AdminPolicyVersionListResponse.from(
                adminService.list(policyType, locale, page, size));
    }


    @Operation(
            operationId = "createAdminPolicyVersion",
            summary = "L47 — soạn phiên bản mới (bản nháp)",
            description = """
                    Chỉ `DPO`. `effectiveFrom` phải ở TƯƠNG LAI (⇒ `422 POLICY_VERSION_INVALID`) \
                    — nếu không, bản vừa soạn có hiệu lực ngay và bước publish của L48 bị vòng \
                    qua. `requiresReconsent = true` BẮT BUỘC kèm `affectedPurposes` không rỗng \
                    (p15 REQ-VER-02). Trùng `(policyType, version, locale)` ⇒ \
                    `409 POLICY_VERSION_EXISTS`. `reason` bắt buộc ≥ 10 ký tự.""")
    @PostMapping
    public ResponseEntity<AdminPolicyVersionResponse> create(
            @CurrentUser SecurityPrincipal principal,
            @Valid @RequestBody CreatePolicyVersionRequest request,
            HttpServletRequest httpRequest) {
        AdminGuard.requireAnyRole(principal, WRITE_ROLES);
        PolicyVersionAdminService.AdminPolicyRow row = adminService.createDraft(
                parseType(request.policyType()),
                request.version(),
                request.localeOrDefault(),
                request.title(),
                request.contentMd(),
                request.contentUrl(),
                request.summaryOfChanges(),
                Boolean.TRUE.equals(request.requiresReconsent()),
                request.affectedPurposes(),
                request.effectiveFrom(),
                action(principal, request.reason(), httpRequest));
        AdminPolicyVersionResponse body = AdminPolicyVersionResponse.from(row);
        return ResponseEntity.created(URI.create("/api/v1/admin/policy-versions/" + body.id()))
                .body(body);
    }


    @Operation(
            operationId = "publishAdminPolicyVersion",
            summary = "L48 — publish, có thể kích hoạt xin lại consent toàn hệ thống",
            description = """
                    Chỉ `DPO`. Ghi `published_by`, chốt `effective_from`, và đóng \
                    `effective_to` của bản đang hiệu lực cùng `(policyType, locale)`. Bản đã \
                    publish ⇒ `409 POLICY_VERSION_ALREADY_PUBLISHED`. Khi bản này có \
                    `requiresReconsent = true`, cổng consent (p15 REQ-VER-03) tự chặn mọi user \
                    cho tới khi họ xác nhận lại các purpose trong `affectedPurposes` — endpoint \
                    này KHÔNG ghi gì vào dữ liệu người dùng. `reason` bắt buộc ≥ 10 ký tự.""")
    @PostMapping("/{id}/publish")
    public ResponseEntity<AdminPolicyVersionResponse> publish(
            @CurrentUser SecurityPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody PublishPolicyVersionRequest request,
            HttpServletRequest httpRequest) {
        AdminGuard.requireAnyRole(principal, WRITE_ROLES);
        PolicyVersionAdminService.AdminPolicyRow row = adminService.publish(
                id, request.effectiveFrom(), action(principal, request.reason(), httpRequest));
        return ResponseEntity.status(HttpStatus.OK).body(AdminPolicyVersionResponse.from(row));
    }


    private static PolicyType parseType(String policyType) {
        try {
            return PolicyType.valueOf(policyType.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException(AdminPolicyErrorCode.POLICY_VERSION_INVALID, "policyType");
        }
    }

    private static PolicyVersionAdminService.PolicyAdminAction action(
            SecurityPrincipal principal, String reason, HttpServletRequest request) {
        return new PolicyVersionAdminService.PolicyAdminAction(
                principal.userId(),
                actorRole(principal),
                AdminGuard.requireReason(reason),
                request.getHeader("X-Request-Id"),
                request.getRemoteAddr(),
                request.getHeader("User-Agent"));
    }

    private static String actorRole(SecurityPrincipal principal) {
        for (String candidate : List.of("DPO", "ADMIN_SUPER")) {
            if (AdminGuard.hasAnyRole(principal, Set.of(candidate))) {
                return candidate;
            }
        }
        return principal.roles().stream().findFirst().orElse(null);
    }
}
