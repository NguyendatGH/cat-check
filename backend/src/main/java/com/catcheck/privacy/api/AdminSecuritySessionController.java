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

/**
 * L62 — {@code POST /api/v1/admin/security/sessions/purge-all}: thu hồi TOÀN BỘ phiên của
 * mọi user. Đây là đường "Ưu tiên 1" của runbook <b>R1</b> (p11 §11.13.4) — có audit tự
 * động, thay cho việc gõ tay {@code DELETE FROM spring_session} trên DB production.
 *
 * <p><b>Chỉ {@code ADMIN_SUPER}</b> (p8 §8.4.12 ô L62). Ô này không có số {@code Qxx} trong
 * ma trận p14 §14.2.2 — nó là hành động vận hành sự cố, không phải một khả năng của màn
 * quản trị thường ngày.</p>
 *
 * <p><b>Hệ quả khi bấm:</b> tất cả người dùng phải đăng nhập lại; mọi admin phải nhập lại
 * TOTP (kể cả người vừa bấm — phiên của chính họ cũng nằm trong "toàn bộ"). §11.13.4 chấp
 * nhận điều đó và yêu cầu có sẵn thông báo in-app giải thích để tổng đài không bị ngập.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/security/sessions")
@Tag(name = "Quản trị phiên toàn hệ thống", description = "Runbook R1 — thu hồi toàn bộ phiên khi có sự cố")
public class AdminSecuritySessionController {

    /** p8 L62 {@code R:ADMIN_SUPER} — không mở cho DPO: DPO giữ hồ sơ, Super thao tác hệ thống. */
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
