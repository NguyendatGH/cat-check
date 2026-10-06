package com.catcheck.privacy.api;

import com.catcheck.privacy.api.dto.AdminDsarRequestResponse;
import com.catcheck.privacy.api.dto.AdminCreateDsarRequest;
import com.catcheck.privacy.api.dto.AdminDsarTransitionRequest;
import com.catcheck.privacy.api.dto.AdminDsarExportResponse;
import com.catcheck.privacy.api.dto.AdminErasureApprovalResponse;
import com.catcheck.privacy.api.dto.AdminReasonRequest;
import com.catcheck.privacy.application.AdminDsarService;
import com.catcheck.privacy.application.AdminErasureApprovalService;
import com.catcheck.privacy.application.export.DataExportJobService;
import com.catcheck.privacy.application.RequestEvidence;
import com.catcheck.privacy.domain.DsarChannel;
import com.catcheck.privacy.domain.DsarRequest;
import com.catcheck.privacy.domain.DsarRequestType;
import com.catcheck.privacy.domain.DsarStatus;
import com.catcheck.privacy.domain.port.DsarRequestPort;
import com.catcheck.shared.security.AdminGuard;
import com.catcheck.shared.security.CurrentUser;
import com.catcheck.shared.security.SecurityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/privacy/requests")
@Tag(name = "Quản trị DSAR", description = "Tra cứu yêu cầu quyền dữ liệu với PII đã che")
public class AdminPrivacyController {
    private static final Set<String> READ_ROLES = Set.of("ADMIN_SUPER", "ADMIN_SUPPORT", "DPO");
    private static final int MAX_PAGE_SIZE = 100;
    /** L52: p8 cho {@code DPO,ADMIN_SUPER}; p14 Q10 siết ADMIN_SUPER lại ở tầng service. */
    private static final Set<String> EXPORT_ROLES = Set.of("ADMIN_SUPER", "DPO");
    /** L53: {@code R:DPO} — p14 Q9 để ADMIN_SUPER là ❌ (quy tắc hai người, p15 REQ-RBAC-03). */
    private static final Set<String> ERASURE_APPROVAL_ROLES = Set.of("DPO");

    private final DsarRequestPort dsarRequestPort;
    private final AdminDsarService adminDsarService;
    private final DataExportJobService dataExportJobService;
    private final AdminErasureApprovalService erasureApprovalService;

    public AdminPrivacyController(DsarRequestPort dsarRequestPort,
                                  AdminDsarService adminDsarService,
                                  DataExportJobService dataExportJobService,
                                  AdminErasureApprovalService erasureApprovalService) {
        this.dsarRequestPort = dsarRequestPort;
        this.adminDsarService = adminDsarService;
        this.dataExportJobService = dataExportJobService;
        this.erasureApprovalService = erasureApprovalService;
    }

    @Operation(operationId = "createAdminPrivacyRequest", summary = "L54 — tạo DSAR thay mặt user")
    @PostMapping
    public AdminDsarRequestResponse create(
            @CurrentUser SecurityPrincipal principal,
            @Valid @RequestBody AdminCreateDsarRequest body,
            HttpServletRequest request) {
        AdminGuard.requireAnyRole(principal, Set.of("ADMIN_SUPER", "DPO"));
        String reason = AdminGuard.requireReason(body.reason());
        DsarRequestType type = parseRequiredType(body.requestType());
        DsarChannel channel = parseChannel(body.channel());
        return AdminDsarRequestResponse.from(adminDsarService.create(
                principal.userId(), actorRole(principal), body.userId(), type, channel, reason, evidence(request)));
    }

    @Operation(operationId = "listAdminPrivacyRequests", summary = "L49 — danh sách DSAR admin")
    @GetMapping
    public PageResponse list(@CurrentUser SecurityPrincipal principal,
                             @RequestParam(required = false) String requestType,
                             @RequestParam(required = false) String status,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "20") int size) {
        AdminGuard.requireAnyRole(principal, READ_ROLES);
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        DsarRequestType type = parseType(requestType);
        DsarStatus parsedStatus = parseStatus(status);
        List<AdminDsarRequestResponse> items = dsarRequestPort.findForAdmin(type, parsedStatus,
                safePage * safeSize, safeSize).stream().map(AdminDsarRequestResponse::from).toList();
        long total = dsarRequestPort.countForAdmin(type, parsedStatus);
        return PageResponse.of(items, safePage, safeSize, total);
    }

    @Operation(operationId = "getAdminPrivacyRequest", summary = "L50 — chi tiết DSAR admin")
    @GetMapping("/{requestId}")
    public AdminDsarRequestResponse detail(@CurrentUser SecurityPrincipal principal,
                                           @PathVariable UUID requestId) {
        AdminGuard.requireAnyRole(principal, READ_ROLES);
        return dsarRequestPort.findById(requestId).map(AdminDsarRequestResponse::from)
                .orElseThrow(() -> new com.catcheck.shared.error.BusinessRuleException(PrivacyErrorCode.DSAR_NOT_FOUND));
    }

    @Operation(operationId = "transitionAdminPrivacyRequest", summary = "L51 — xử lý DSAR")
    @PostMapping("/{requestId}/transition")
    public AdminDsarRequestResponse transition(
            @CurrentUser SecurityPrincipal principal,
            @PathVariable UUID requestId,
            @Valid @RequestBody AdminDsarTransitionRequest body,
            HttpServletRequest request) {
        AdminGuard.requireAnyRole(principal, READ_ROLES);
        String action = body.action() == null ? "" : body.action().strip().toUpperCase(Locale.ROOT);
        String reason = Set.of("EXTEND", "REJECT").contains(action)
                ? AdminGuard.requireReason(body.reason()) : body.reason();
        return AdminDsarRequestResponse.from(adminDsarService.transition(
                principal.userId(), actorRole(principal), requestId, action, reason, body.extendedTo(), evidence(request)));
    }

    /**
     * L52 — sinh gói dữ liệu cá nhân thay mặt chủ thể, {@code 202 Accepted}.
     *
     * <p>Điều kiện hẹp hơn của p14 Q10 (ADMIN_SUPER chỉ xuất yêu cầu mình là
     * {@code handled_by}) nằm ở service: ở đây chưa đọc {@code dsar_request} nên chưa biết
     * ai đang xử lý.</p>
     */
    @Operation(operationId = "exportAdminPrivacyRequest", summary = "L52 — xuất gói dữ liệu cá nhân thay user")
    @PostMapping("/{requestId}/export")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
    public AdminDsarExportResponse export(
            @CurrentUser SecurityPrincipal principal,
            @PathVariable UUID requestId,
            @Valid @RequestBody AdminReasonRequest body,
            HttpServletRequest request) {
        AdminGuard.requireAnyRole(principal, EXPORT_ROLES);
        String reason = AdminGuard.requireReason(body.reason());
        return AdminDsarExportResponse.from(dataExportJobService.enqueueOnBehalf(
                requestId, principal.userId(), actorRole(principal), reason, evidence(request)));
    }

    /** L53 — phê duyệt + thực thi xoá tài khoản, quy tắc hai người (p15 REQ-RBAC-03). */
    @Operation(operationId = "approveAdminPrivacyErasure", summary = "L53 — duyệt và thực thi xoá tài khoản")
    @PostMapping("/{requestId}/approve-erasure")
    public AdminErasureApprovalResponse approveErasure(
            @CurrentUser SecurityPrincipal principal,
            @PathVariable UUID requestId,
            @Valid @RequestBody AdminReasonRequest body,
            HttpServletRequest request) {
        AdminGuard.requireAnyRole(principal, ERASURE_APPROVAL_ROLES);
        String reason = AdminGuard.requireReason(body.reason());
        return AdminErasureApprovalResponse.from(erasureApprovalService.approve(
                principal.userId(), requestId, reason, evidence(request)));
    }

    private static String actorRole(SecurityPrincipal principal) {
        return principal.roles().stream()
                .map(role -> role.startsWith("ROLE_") ? role.substring("ROLE_".length()) : role)
                .filter(Set.of("DPO", "ADMIN_SUPER", "ADMIN_SUPPORT")::contains)
                .findFirst()
                .orElse("ADMIN_SUPPORT");
    }

    private static DsarRequestType parseRequiredType(String value) {
        try {
            return DsarRequestType.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new com.catcheck.shared.error.BusinessRuleException(PrivacyErrorCode.VALIDATION_FAILED, "requestType");
        }
    }

    private static DsarChannel parseChannel(String value) {
        try {
            DsarChannel channel = DsarChannel.valueOf(value.strip().toUpperCase(Locale.ROOT));
            if (channel == DsarChannel.SELF_SERVICE) {
                throw new IllegalArgumentException("self-service");
            }
            return channel;
        } catch (RuntimeException ex) {
            throw new com.catcheck.shared.error.BusinessRuleException(PrivacyErrorCode.VALIDATION_FAILED, "channel");
        }
    }

    private static RequestEvidence evidence(HttpServletRequest request) {
        return RequestEvidence.of(request.getHeader("X-Request-Id"), request.getRemoteAddr(), request.getHeader("User-Agent"));
    }

    private static DsarRequestType parseType(String value) {
        return value == null || value.isBlank() ? null : DsarRequestType.valueOf(value.strip().toUpperCase(Locale.ROOT));
    }

    private static DsarStatus parseStatus(String value) {
        return value == null || value.isBlank() ? null : DsarStatus.valueOf(value.strip().toUpperCase(Locale.ROOT));
    }

    public record PageResponse(List<AdminDsarRequestResponse> items, int page, int size,
                               long totalElements, int totalPages, boolean hasMore) {
        static PageResponse of(List<AdminDsarRequestResponse> items, int page, int size, long total) {
            int pages = size <= 0 ? 0 : (int) Math.ceilDiv(total, size);
            return new PageResponse(items, page, size, total, pages, (long) (page + 1) * size < total);
        }
    }
}
