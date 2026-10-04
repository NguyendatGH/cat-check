package com.catcheck.identity.api;

import com.catcheck.identity.api.dto.AdminMfaResetRequestResponse;
import com.catcheck.identity.api.dto.AdminRoleRequest;
import com.catcheck.identity.application.AdminStaffService;
import com.catcheck.identity.domain.MfaResetRequest;
import com.catcheck.identity.domain.UserRole;
import com.catcheck.shared.security.AdminGuard;
import com.catcheck.shared.security.CurrentUser;
import com.catcheck.shared.security.SecurityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** L13-L17 — quản trị staff và hàng đợi reset TOTP. */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Quản trị nhân sự", description = "Cấp/gỡ role và duyệt reset MFA hai người")
public class AdminStaffController {

    private static final Set<String> SUPER_ONLY = Set.of("ADMIN_SUPER");
    private final AdminStaffService staffService;

    public AdminStaffController(AdminStaffService staffService) {
        this.staffService = staffService;
    }

    @Operation(operationId = "grantAdminRole", summary = "L13 — cấp role staff")
    @PostMapping("/users/{userId}/roles")
    public void grantRole(@CurrentUser SecurityPrincipal principal, @PathVariable UUID userId,
                          @Valid @RequestBody AdminRoleRequest body, HttpServletRequest request) {
        AdminGuard.requireAnyRole(principal, SUPER_ONLY);
        staffService.grantRole(userId, parseRole(body.role()), AdminUserContext.withReason(
                principal, body.reason(), request));
    }

    @Operation(operationId = "revokeAdminRole", summary = "L14 — gỡ role staff")
    @DeleteMapping("/users/{userId}/roles/{role}")
    public void revokeRole(@CurrentUser SecurityPrincipal principal, @PathVariable UUID userId,
                           @PathVariable String role, HttpServletRequest request) {
        AdminGuard.requireAnyRole(principal, SUPER_ONLY);
        staffService.revokeRole(userId, parseRole(role), AdminUserContext.withReason(
                principal, "Gỡ role: " + role, request));
    }

    @Operation(operationId = "requestAdminTotpReset", summary = "L15 — yêu cầu reset TOTP")
    @PostMapping("/users/{userId}/mfa/totp/reset")
    public AdminMfaResetRequestResponse requestReset(@CurrentUser SecurityPrincipal principal,
                                                      @PathVariable UUID userId,
                                                      @Valid @RequestBody com.catcheck.identity.api.dto.AdminUserReasonRequest body,
                                                      HttpServletRequest request) {
        AdminGuard.requireAnyRole(principal, SUPER_ONLY);
        return AdminMfaResetRequestResponse.from(staffService.requestTotpReset(userId,
                AdminUserContext.withReason(principal, body.reason(), request)));
    }

    @Operation(operationId = "listAdminTotpResetRequests", summary = "L17 — hàng đợi reset TOTP")
    @GetMapping("/totp-reset-requests")
    public List<AdminMfaResetRequestResponse> pending(@CurrentUser SecurityPrincipal principal) {
        AdminGuard.requireAnyRole(principal, Set.of("ADMIN_SUPER", "DPO"));
        return staffService.pendingResetRequests().stream().map(AdminMfaResetRequestResponse::from).toList();
    }

    @Operation(operationId = "approveAdminTotpReset", summary = "L16 — duyệt reset TOTP")
    @PostMapping("/totp-reset-requests/{requestId}/approve")
    public AdminMfaResetRequestResponse approve(@CurrentUser SecurityPrincipal principal,
                                                 @PathVariable UUID requestId,
                                                 HttpServletRequest request) {
        AdminGuard.requireAnyRole(principal, Set.of("DPO"));
        return AdminMfaResetRequestResponse.from(staffService.approveTotpReset(requestId,
                AdminUserContext.withRole(principal, "DPO", "Duyệt reset TOTP", request)));
    }

    private static UserRole parseRole(String value) {
        try {
            return UserRole.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("role không hợp lệ");
        }
    }
}
