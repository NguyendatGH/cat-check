package com.catcheck.admin.api;

import com.catcheck.admin.api.dto.AdminOpsPageResponse;
import com.catcheck.admin.api.dto.AdminMetricsResponse;
import com.catcheck.admin.api.dto.AuditLogResponse;
import com.catcheck.admin.api.dto.JobRunResponse;
import com.catcheck.admin.api.dto.OutboxEntryResponse;
import com.catcheck.admin.api.dto.AdminMaintenanceRequest;
import com.catcheck.admin.api.dto.AdminMaintenanceResponse;
import com.catcheck.admin.application.AdminMaintenanceService;
import com.catcheck.admin.application.AdminOpsQueryService;
import com.catcheck.admin.domain.JobRunRow;
import com.catcheck.admin.domain.AuditLogRow;
import com.catcheck.admin.domain.OutboxRow;
import com.catcheck.shared.security.AdminGuard;
import com.catcheck.shared.security.CurrentUser;
import com.catcheck.shared.security.SecurityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Hai màn vận hành chỉ-đọc — L64 {@code GET /admin/jobs/runs} và L66
 * {@code GET /admin/notifications/outbox} (p8 §8.4.12 mục (f)).
 *
 * <p><b>Chỉ đọc là cố ý.</b> L65 (chạy job bằng tay) và L67 (gửi lại bản ghi {@code FAILED}) là
 * hành động GHI vào miền của module khác và phải đi qua cổng của module đó
 * ({@code shared.job} / {@code notification}); viết một câu {@code UPDATE} từ {@code admin} sẽ
 * bỏ qua toàn bộ luật retry/backoff/dedupe mà {@code notification} đang giữ. Handoff H15.106.</p>
 *
 * <p><b>Vai trò</b> theo p8: L64 {@code R:ADMIN_SUPER,DPO}; L66 {@code R:ADMIN_SUPER}.</p>
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Quản trị vận hành", description = "L64, L66 — job_run và outbox (chỉ đọc)")
public class AdminOpsController {

    /** p8 L64: {@code R:ADMIN_SUPER,DPO}. */
    static final Set<String> JOB_READ_ROLES = Set.of("ADMIN_SUPER", "DPO");

    /** p8 L66: {@code R:ADMIN_SUPER} — bảng này chứa địa chỉ email người dùng. */
    static final Set<String> OUTBOX_READ_ROLES = Set.of("ADMIN_SUPER");
    static final Set<String> AUDIT_READ_ROLES = Set.of("ADMIN_SUPER", "ADMIN_SUPPORT", "DPO");

    private static final int MAX_PAGE_SIZE = 200;

    private static final List<String> ADMIN_ROLE_PRIORITY =
            List.of("ADMIN_SUPER", "DPO", "ADMIN_SUPPORT", "ADMIN_CATALOG");

    private final AdminOpsQueryService opsQueryService;
    private final AdminMaintenanceService maintenanceService;

    public AdminOpsController(AdminOpsQueryService opsQueryService, AdminMaintenanceService maintenanceService) {
        this.opsQueryService = opsQueryService;
        this.maintenanceService = maintenanceService;
    }

    @Operation(operationId = "updateAdminMaintenance", summary = "L71 — bật/tắt bảo trì công khai")
    @PutMapping("/system/maintenance")
    public AdminMaintenanceResponse maintenance(@CurrentUser SecurityPrincipal principal,
                                                 @Valid @RequestBody AdminMaintenanceRequest body,
                                                 HttpServletRequest request) {
        AdminGuard.requireAnyRole(principal, Set.of("ADMIN_SUPER"));
        AdminMaintenanceService.Result result = maintenanceService.update(body.active(), body.until(),
                principal.userId(), actorRole(principal), body.reason().strip(), request.getHeader("X-Request-Id"),
                request.getRemoteAddr(), request.getHeader("User-Agent"));
        return new AdminMaintenanceResponse(Boolean.parseBoolean(result.active()),
                result.until().isBlank() ? null : result.until(), result.updatedAt());
    }

    // ------------------------------------------------------------------ L64

    @Operation(
            operationId = "listAdminJobRuns",
            summary = "L64 — các lần chạy job nền",
            description = "`job_run` là bảng log job duy nhất (p8 L64/H9.1). Lọc theo `jobName` và "
                    + "`status`; mới nhất trước.")
    @GetMapping("/jobs/runs")
    public AdminOpsPageResponse<JobRunResponse> jobRuns(
            @CurrentUser SecurityPrincipal principal,
            @RequestParam(required = false) String jobName,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        AdminGuard.requireAnyRole(principal, JOB_READ_ROLES);
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        AdminOpsQueryService.Page<JobRunRow> result =
                opsQueryService.jobRuns(jobName, upper(status), safePage, safeSize);
        return AdminOpsPageResponse.of(
                result.items().stream().map(JobRunResponse::from).toList(),
                safePage, safeSize, result.totalElements());
    }

    // ------------------------------------------------------------------ L66

    @Operation(
            operationId = "listAdminNotificationOutbox",
            summary = "L66 — email_outbox + notification_outbox",
            description = "Hợp nhất hai bảng. `channel=EMAIL|PUSH`, `status=PENDING|SENT|FAILED` "
                    + "(`?status=FAILED` là bộ lọc dead-letter mà p8 nêu). Địa chỉ email đã mask.")
    @GetMapping("/notifications/outbox")
    public AdminOpsPageResponse<OutboxEntryResponse> outbox(
            @CurrentUser SecurityPrincipal principal,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        AdminGuard.requireAnyRole(principal, OUTBOX_READ_ROLES);
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        AdminOpsQueryService.Page<OutboxRow> result = opsQueryService.outbox(
                upper(channel), upper(status), safePage, safeSize,
                principal.userId(), actorRole(principal),
                httpRequest.getHeader("X-Request-Id"),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent"));
        return AdminOpsPageResponse.of(
                result.items().stream().map(OutboxEntryResponse::from).toList(),
                safePage, safeSize, result.totalElements());
    }

    @Operation(
            operationId = "listAdminAuditLogs",
            summary = "L68 — nhật ký kiểm toán",
            description = "DPO/Super xem toàn bộ; Support chỉ xem các thao tác do chính mình thực hiện."
    )
    @GetMapping("/audit-logs")
    public AdminOpsPageResponse<AuditLogResponse> auditLogs(
            @CurrentUser SecurityPrincipal principal,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String actorType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        AdminGuard.requireAnyRole(principal, AUDIT_READ_ROLES);
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        boolean supportOnly = AdminGuard.hasAnyRole(principal, Set.of("ADMIN_SUPPORT"))
                && !AdminGuard.hasAnyRole(principal, Set.of("ADMIN_SUPER", "DPO"));
        AdminOpsQueryService.Page<AuditLogRow> resultPage = opsQueryService.auditLogs(
                upper(action), upper(result), upper(actorType), supportOnly ? principal.userId() : null,
                safePage, safeSize);
        return AdminOpsPageResponse.of(resultPage.items().stream().map(AuditLogResponse::from).toList(),
                safePage, safeSize, resultPage.totalElements());
    }

    @Operation(
            operationId = "getAdminDashboardMetrics",
            summary = "L63 — số liệu tổng quan vận hành",
            description = "Snapshot chỉ đọc từ các bảng nghiệp vụ; không bao gồm PII hoặc nội dung chi tiết.")
    @GetMapping("/metrics/dashboard")
    public AdminMetricsResponse dashboardMetrics(@CurrentUser SecurityPrincipal principal) {
        AdminGuard.requireAnyRole(principal, Set.of("ADMIN_SUPER", "DPO"));
        return AdminMetricsResponse.from(opsQueryService.metrics());
    }

    /* ---------------------------------------------------------------- helper */

    /**
     * Chuẩn hoá bộ lọc enum về chữ hoa. KHÔNG tự loại giá trị lạ: cả hai cột là
     * {@code VARCHAR + CHECK} nên một giá trị không tồn tại chỉ trả 0 dòng, không gây lỗi, và
     * đó là câu trả lời đúng cho "lọc theo một trạng thái không có".
     */
    private static String upper(String value) {
        return value == null || value.isBlank() ? null : value.strip().toUpperCase(Locale.ROOT);
    }

    private static String actorRole(SecurityPrincipal principal) {
        for (String candidate : ADMIN_ROLE_PRIORITY) {
            if (AdminGuard.hasAnyRole(principal, Set.of(candidate))) {
                return candidate;
            }
        }
        return principal.roles().stream().findFirst().orElse(null);
    }
}
