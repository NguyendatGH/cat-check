package com.catcheck.privacy.api;

import com.catcheck.privacy.api.dto.PurgeAllSessionsRequest;
import com.catcheck.privacy.api.dto.PurgeAllSessionsResponse;
import com.catcheck.privacy.application.GlobalSessionPurgeService;
import com.catcheck.privacy.application.RequestEvidence;
import com.catcheck.shared.security.AdminGuard;
import com.catcheck.shared.security.CurrentUser;
import com.catcheck.shared.security.SecurityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin/security/sessions")
@Tag(name = "Quản trị phiên toàn hệ thống", description = "Runbook R1 — thu hồi toàn bộ phiên khi có sự cố")
public class AdminSecuritySessionController {

    private static final Set<String> PURGE_ROLES = Set.of("ADMIN_SUPER");

    private final GlobalSessionPurgeService purgeService;

    public AdminSecuritySessionController(GlobalSessionPurgeService purgeService) {
        this.purgeService = purgeService;
    }

    @Operation(operationId = "purgeAllAdminSecuritySessions",
            summary = "L62 — thu hồi toàn bộ phiên của mọi user (runbook R1)")
    @PostMapping("/purge-all")
    public PurgeAllSessionsResponse purgeAll(@CurrentUser SecurityPrincipal principal,
                                             @Valid @RequestBody PurgeAllSessionsRequest body,
                                             HttpServletRequest request) {
        AdminGuard.requireAnyRole(principal, PURGE_ROLES);
        String reason = AdminGuard.requireReason(body.reason());
        return PurgeAllSessionsResponse.from(purgeService.purgeAll(
                principal.userId(), "ADMIN_SUPER", body.incidentId(), reason,
                RequestEvidence.of(request.getHeader("X-Request-Id"), request.getRemoteAddr(),
                        request.getHeader("User-Agent"))));
    }
}
