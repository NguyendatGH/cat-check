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

/**
 * Tra cứu và hỗ trợ tài khoản từ màn quản trị — L1, L2, L3, L7, L8, L11, L12
 * (p8 §8.4.12 mục (a)).
 *
 * <p><b>Đã làm trong gói này</b> là phần lõi để vận hành MVP: tìm/xem/khoá/mở khoá/xem phiên/
 * thu hồi phiên và bỏ mask PII. <b>Chưa làm</b>: L4 (hồ sơ mèo), L5 (số liệu scan), L6 (ảnh
 * scan — chỉ DPO khi có DSAR mở), L9/L10 (credit + điều chỉnh credit thủ công), L13–L17 (cấp/gỡ
 * vai trò, reset TOTP hai người), L18 (gán lại mèo cho scan). Xem handoff H15.106.</p>
 *
 * <p><b>Vai trò</b> theo p11 §11.5.4 (ma trận quyền duy nhất của hệ thống): đọc cho
 * {@code ADMIN_SUPER}/{@code ADMIN_SUPPORT}/{@code DPO}; khoá, mở khoá, xem và thu hồi phiên chỉ
 * {@code ADMIN_SUPER} (p8 L7/L8/L11/L12).</p>
 *
 * <p><b>Chưa có:</b> step-up re-auth ({@code SW}/{@code S1} của p8 — một lớp NGOÀI
 * {@code mfaLevel} mà bộ gác chuỗi filter đã kiểm). Handoff H15.102.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Quản trị người dùng", description = "L1–L3, L7–L8, L11–L12 — tra cứu, khoá, phiên đăng nhập")
public class AdminUserController {

    /** p11 §11.5.4: Support/Super đọc "dữ liệu che"; DPO đọc đầy đủ khi xử lý DSAR. */
    static final Set<String> READ_ROLES = Set.of("ADMIN_SUPER", "ADMIN_SUPPORT", "DPO");

    /** p8 L3: bỏ mask cho Super/Support/DPO (Q2). */
    static final Set<String> UNMASK_ROLES = READ_ROLES;

    /** p8 L7/L8/L11/L12: khoá, mở khoá, xem/thu hồi phiên là {@code ADMIN_SUPER} duy nhất. */
    static final Set<String> SUPER_ONLY = Set.of("ADMIN_SUPER");

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    // ------------------------------------------------------------------- L1

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

    // ------------------------------------------------------------------- L2

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

    // ------------------------------------------------------------------- L3

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
        // Endpoint DUY NHẤT trả PII thô của một người khác: không bao giờ được vào cache nào.
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(body);
    }

    // ------------------------------------------------------------------- L7

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

    // ------------------------------------------------------------------- L8

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

    // ------------------------------------------------------------------ L11

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

    // ------------------------------------------------------------------ L12

    /**
     * {@code reason} là query param: {@code DELETE} có body bị nhiều proxy và thư viện HTTP bỏ
     * qua (và {@code fetch} của trình duyệt cũng không gửi body cho {@code DELETE} trong một số
     * cấu hình). Cùng đánh đổi như L22 — xem handoff H15.101.
     */
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

    /* ---------------------------------------------------------------- helper */

    private static UserStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        // Giá trị lạ ⇒ IllegalArgumentException ⇒ 400 VALIDATION_FAILED, không im lặng bỏ lọc
        // (bỏ lọc im lặng nghĩa là admin tưởng đang xem "chỉ LOCKED" mà thực ra thấy tất cả).
        return UserStatus.valueOf(status.strip().toUpperCase(Locale.ROOT));
    }
}
