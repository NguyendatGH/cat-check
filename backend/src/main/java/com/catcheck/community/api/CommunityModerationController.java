package com.catcheck.community.api;

import com.catcheck.community.api.dto.CommunityModerationRequest;
import com.catcheck.community.api.dto.CommunityReportPageResponse;
import com.catcheck.community.api.dto.CommunityReportResponse;
import com.catcheck.community.application.CommunityModerationService;
import com.catcheck.shared.security.AdminGuard;
import com.catcheck.shared.security.CurrentUser;
import com.catcheck.shared.security.SecurityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/community/reports")
@Tag(name = "Quản trị cộng đồng", description = "F18 — hàng đợi report và moderation")
public class CommunityModerationController {
    private static final Set<String> MODERATOR_ROLES = Set.of("MODERATOR", "ADMIN_SUPER");

    private final CommunityModerationService service;

    public CommunityModerationController(CommunityModerationService service) { this.service = service; }

    @GetMapping
    @Operation(operationId = "listCommunityReports")
    public CommunityReportPageResponse list(@CurrentUser SecurityPrincipal principal,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        AdminGuard.requireAnyRole(principal, MODERATOR_ROLES);
        var result = service.list(status, page, size);
        return new CommunityReportPageResponse(result.items().stream().map(CommunityReportResponse::from).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages(), result.hasMore());
    }

    @PostMapping("/{reportId}/resolve")
    @Operation(operationId = "moderateCommunityReport")
    public CommunityReportResponse moderate(@CurrentUser SecurityPrincipal principal,
                                             @PathVariable UUID reportId,
                                             @Valid @RequestBody CommunityModerationRequest request,
                                             HttpServletRequest httpRequest) {
        AdminGuard.requireAnyRole(principal, MODERATOR_ROLES);
        String reason = AdminGuard.requireReason(request.reason());
        String role = principal.roles().stream().filter(MODERATOR_ROLES::contains).findFirst().orElse("MODERATOR");
        var context = new CommunityModerationService.ModerationContext(principal.userId(), role,
                httpRequest.getHeader("X-Request-Id"), httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        return CommunityReportResponse.from(service.moderate(reportId, request.action(), reason, context));
    }
}
