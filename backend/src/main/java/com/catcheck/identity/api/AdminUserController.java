package com.catcheck.identity.api;

import com.catcheck.identity.api.dto.AdminLockResponse;
import com.catcheck.identity.api.dto.AdminSessionItem;
import com.catcheck.identity.api.dto.AdminSessionListResponse;
import com.catcheck.identity.api.dto.AdminUnmaskResponse;
import com.catcheck.identity.api.dto.AdminUserDetailResponse;
import com.catcheck.identity.api.dto.AdminUserPageResponse;
import com.catcheck.identity.api.dto.AdminUserReasonRequest;
import com.catcheck.identity.api.dto.AdminUserSummaryResponse;
import com.catcheck.identity.application.AdminUserService;
import com.catcheck.identity.domain.AdminUserSummary;
import com.catcheck.identity.domain.UserStatus;
import com.catcheck.shared.security.AdminGuard;
import com.catcheck.shared.security.CurrentUser;
import com.catcheck.shared.security.SecurityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Quản trị người dùng", description = "L1–L3, L7–L8, L11–L12 — tra cứu, khoá, phiên đăng nhập")
public class AdminUserController {

    static final Set<String> READ_ROLES = Set.of("ADMIN_SUPER", "ADMIN_SUPPORT", "DPO");

    static final Set<String> UNMASK_ROLES = READ_ROLES;

    static final Set<String> SUPER_ONLY = Set.of("ADMIN_SUPER");

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }


    @Operation(
            operationId = "listAdminUsers",
            summary = "L1 — tìm/lọc người dùng",
            description = "PII mask sẵn trong response (p15 REQ-RBAC-01). `email` khớp CHÍNH XÁC, "
                    + "không tìm mờ theo tên hay số điện thoại (p14 §14.3.2 mục 2).")
    @GetMapping
    public AdminUserPageResponse<AdminUserSummaryResponse> list(
            @CurrentUser SecurityPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String packageCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        AdminGuard.requireAnyRole(principal, READ_ROLES);
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        AdminUserService.Page<AdminUserSummary> result = adminUserService.search(
                parseStatus(status), email, packageCode, safePage, safeSize,
                AdminUserContext.withoutReason(principal, httpRequest));
        return AdminUserPageResponse.of(
                result.items().stream().map(AdminUserSummaryResponse::from).toList(),
                safePage, safeSize, result.totalElements());
    }


    @Operation(
            operationId = "getAdminUserDetail",
            summary = "L2 — chi tiết một người dùng",
            description = "Hồ sơ đã mask, trạng thái, gói cao nhất, số mèo, số scan, số phiên đang mở.")
    @GetMapping("/{userId}")
    public AdminUserDetailResponse detail(
            @CurrentUser SecurityPrincipal principal,
            @PathVariable UUID userId,
            HttpServletRequest httpRequest) {
        AdminGuard.requireAnyRole(principal, READ_ROLES);
        return AdminUserDetailResponse.from(adminUserService.detail(
                userId, AdminUserContext.withoutReason(principal, httpRequest)));
    }


    @Operation(
            operationId = "unmaskAdminUserPii",
            summary = "L3 — bỏ mask email/phone",
            description = "`reason` bắt buộc ≥ 10 ký tự, ghi `audit_log` action ADMIN_PII_UNMASKED. "
                    + "Giá trị thật trả ĐÚNG MỘT LẦN trong response này; `unmaskedUntil` là mốc UI "
                    + "phải tự che lại (p15 REQ-RBAC-01).")
    @PostMapping("/{userId}/unmask")
    public ResponseEntity<AdminUnmaskResponse> unmask(
            @CurrentUser SecurityPrincipal principal,
            @PathVariable UUID userId,
            @Valid @RequestBody AdminUserReasonRequest request,
            HttpServletRequest httpRequest) {
        AdminGuard.requireAnyRole(principal, UNMASK_ROLES);
        AdminUnmaskResponse body = AdminUnmaskResponse.from(adminUserService.unmask(
                userId, AdminUserContext.withReason(principal, request.reason(), httpRequest)));
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(body);
    }


    @Operation(
            operationId = "lockAdminUser",
            summary = "L7 — khoá tài khoản",
            description = "status ⇒ LOCKED và thu hồi MỌI phiên ngay (p11 §11.1.8). Tự khoá mình "
                    + "⇒ 409 ADMIN_CANNOT_MODIFY_SELF.")
    @PostMapping("/{userId}/lock")
    public AdminLockResponse lock(
            @CurrentUser SecurityPrincipal principal,
            @PathVariable UUID userId,
            @Valid @RequestBody AdminUserReasonRequest request,
            HttpServletRequest httpRequest) {
        AdminGuard.requireAnyRole(principal, SUPER_ONLY);
        return AdminLockResponse.from(adminUserService.lock(
                userId, AdminUserContext.withReason(principal, request.reason(), httpRequest)));
    }


    @Operation(
            operationId = "unlockAdminUser",
            summary = "L8 — mở khoá tài khoản",
            description = "Không tự tạo lại phiên nào (p8 L8).")
    @PostMapping("/{userId}/unlock")
    public AdminLockResponse unlock(
            @CurrentUser SecurityPrincipal principal,
            @PathVariable UUID userId,
            @Valid @RequestBody AdminUserReasonRequest request,
            HttpServletRequest httpRequest) {
        AdminGuard.requireAnyRole(principal, SUPER_ONLY);
        return AdminLockResponse.from(adminUserService.unlock(
                userId, AdminUserContext.withReason(principal, request.reason(), httpRequest)));
    }


    @Operation(
            operationId = "listAdminUserSessions",
            summary = "L11 — phiên đang mở của một người dùng",
            description = "Dùng khi người dùng báo bị chiếm tài khoản. IP đã che phần cuối.")
    @GetMapping("/{userId}/sessions")
    public AdminSessionListResponse sessions(
            @CurrentUser SecurityPrincipal principal,
            @PathVariable UUID userId,
            HttpServletRequest httpRequest) {
        AdminGuard.requireAnyRole(principal, SUPER_ONLY);
        return AdminSessionListResponse.of(adminUserService
                .sessions(userId, AdminUserContext.withoutReason(principal, httpRequest))
                .stream()
                .map(AdminSessionItem::from)
                .toList());
    }


    @Operation(
            operationId = "revokeAdminUserSessions",
            summary = "L12 — thu hồi mọi phiên của một người dùng",
            description = "`reason` bắt buộc ≥ 10 ký tự (query param).")
    @DeleteMapping("/{userId}/sessions")
    public AdminSessionListResponse revokeSessions(
            @CurrentUser SecurityPrincipal principal,
            @PathVariable UUID userId,
            @RequestParam String reason,
            HttpServletRequest httpRequest) {
        AdminGuard.requireAnyRole(principal, SUPER_ONLY);
        int revoked = adminUserService.revokeAllSessions(
                userId, AdminUserContext.withReason(principal, reason, httpRequest));
        return new AdminSessionListResponse(java.util.List.of(), revoked);
    }


    private static UserStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return UserStatus.valueOf(status.strip().toUpperCase(Locale.ROOT));
    }
}
