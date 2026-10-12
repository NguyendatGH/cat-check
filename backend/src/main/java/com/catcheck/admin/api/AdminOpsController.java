package com.catcheck.admin.api;

import com.catcheck.admin.api.dto.AdminOpsPageResponse;
import com.catcheck.admin.api.dto.AdminMetricsResponse;
import com.catcheck.admin.api.dto.AppSettingListResponse;
import com.catcheck.admin.api.dto.AppSettingResponse;
import com.catcheck.admin.api.dto.AuditLogResponse;
import com.catcheck.admin.api.dto.JobRunResponse;
import com.catcheck.admin.api.dto.OutboxEntryResponse;
import com.catcheck.admin.api.dto.RunJobRequest;
import com.catcheck.admin.api.dto.RunJobResponse;
import com.catcheck.admin.api.dto.SystemBroadcastRequest;
import com.catcheck.admin.api.dto.SystemBroadcastResponse;
import com.catcheck.admin.api.dto.UpdateAppSettingRequest;
import com.catcheck.admin.api.dto.AdminMaintenanceRequest;
import com.catcheck.admin.api.dto.AdminMaintenanceResponse;
import com.catcheck.admin.application.AdminJobControlService;
import com.catcheck.admin.application.AdminMaintenanceService;
import com.catcheck.admin.application.AdminNotificationOpsService;
import com.catcheck.admin.application.AdminOpsQueryService;
import com.catcheck.admin.application.AdminSettingsService;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.admin.domain.AppSettingRow;
import com.catcheck.admin.domain.JobRunRow;
import com.catcheck.admin.domain.AuditLogRow;
import com.catcheck.admin.domain.OutboxRow;
import com.catcheck.notification.api.SystemBroadcastGateway;
import com.catcheck.shared.job.JobOutcome;
import com.catcheck.shared.security.AdminGuard;
import com.catcheck.shared.security.CurrentUser;
import com.catcheck.shared.security.SecurityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Quản trị vận hành", description = "L63–L72 — job_run, outbox, audit, app_setting, bảo trì, broadcast")
public class AdminOpsController {

    static final Set<String> JOB_READ_ROLES = Set.of("ADMIN_SUPER", "DPO");

    static final Set<String> OUTBOX_READ_ROLES = Set.of("ADMIN_SUPER");
    static final Set<String> AUDIT_READ_ROLES = Set.of("ADMIN_SUPER", "ADMIN_SUPPORT", "DPO");

    private static final int MAX_PAGE_SIZE = 200;

    private static final List<String> ADMIN_ROLE_PRIORITY =
            List.of("ADMIN_SUPER", "DPO", "ADMIN_SUPPORT", "ADMIN_CATALOG");

    static final Set<String> DASHBOARD_METRICS_ROLES = Set.copyOf(ADMIN_ROLE_PRIORITY);

    static final Set<String> SUPER_ONLY = Set.of("ADMIN_SUPER");

    static final Set<String> BROADCAST_INCIDENT_ROLES = Set.of("ADMIN_SUPER", "DPO");

    static final String TEMPLATE_PRIVACY_INCIDENT = "PRIVACY_INCIDENT_NOTICE";

    private final AdminOpsQueryService opsQueryService;
    private final AdminMaintenanceService maintenanceService;
    private final AdminJobControlService jobControlService;
    private final AdminNotificationOpsService notificationOpsService;
    private final AdminSettingsService settingsService;

    public AdminOpsController(AdminOpsQueryService opsQueryService,
                              AdminMaintenanceService maintenanceService,
                              AdminJobControlService jobControlService,
                              AdminNotificationOpsService notificationOpsService,
                              AdminSettingsService settingsService) {
        this.opsQueryService = opsQueryService;
        this.maintenanceService = maintenanceService;
        this.jobControlService = jobControlService;
        this.notificationOpsService = notificationOpsService;
        this.settingsService = settingsService;
    }

    @Operation(operationId = "updateAdminMaintenance", summary = "L71 — bật/tắt bảo trì công khai")
    @PutMapping("/system/maintenance")
    public AdminMaintenanceResponse maintenance(@CurrentUser SecurityPrincipal principal,
                                                 @Valid @RequestBody AdminMaintenanceRequest body,
                                                 HttpServletRequest request) {
        AdminGuard.requireAnyRole(principal, Set.of("ADMIN_SUPER"));
        String reason = AdminGuard.requireReason(body.reason());
        AdminMaintenanceService.Result result = maintenanceService.update(body.active(), body.until(),
                principal.userId(), actorRole(principal), reason, request.getHeader("X-Request-Id"),
                request.getRemoteAddr(), request.getHeader("User-Agent"));
        return new AdminMaintenanceResponse(Boolean.parseBoolean(result.active()),
                result.until().isBlank() ? null : result.until(), result.updatedAt());
    }


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
        AdminGuard.requireAnyRole(principal, DASHBOARD_METRICS_ROLES);
        return AdminMetricsResponse.from(opsQueryService.metrics());
    }


    @Operation(
            operationId = "runAdminJob",
            summary = "L65 — kích hoạt thủ công một job nền",
            description = """
                    Ghi `job_run.trigger_type = MANUAL` (p4 §K3). `reason` bắt buộc (≥ 10 ký tự, \
                    thiếu ⇒ 400 REASON_REQUIRED) và `Idempotency-Key` bắt buộc. `dryRun = true` \
                    chỉ đếm, không ghi/không xoá — nhưng VẪN để lại một dòng `job_run` \
                    (p15 REQ-RET-01 + p4 §K3). Tên job lạ ⇒ 404 JOB_NOT_FOUND; job có thật nhưng \
                    chưa đăng ký đường chạy tay ⇒ 409 JOB_NOT_MANUALLY_RUNNABLE; công tắc tổng \
                    `catcheck.jobs.enabled=false` ⇒ 409 JOBS_DISABLED.""")
    @PostMapping("/jobs/{jobName}/run")
    public RunJobResponse runJob(@CurrentUser SecurityPrincipal principal,
                                 @PathVariable String jobName,
                                 @Valid @RequestBody RunJobRequest body,
                                 @RequestHeader(value = "Idempotency-Key", required = false)
                                 String idempotencyKey,
                                 HttpServletRequest request) {
        AdminGuard.requireAnyRole(principal, SUPER_ONLY);
        String reason = AdminGuard.requireReason(body.reason());
        AdminIdempotency.require(idempotencyKey);
        JobOutcome outcome = jobControlService.run(jobName, body.dryRunOrFalse(),
                principal.userId(), actorRole(principal), reason,
                request.getHeader("X-Request-Id"), request.getRemoteAddr(),
                request.getHeader("User-Agent"));
        return new RunJobResponse(jobName, outcome.status().name(), "MANUAL", body.dryRunOrFalse(),
                outcome.itemsProcessed(), outcome.itemsDeleted(), outcome.itemsFailed(),
                outcome.errorSummary());
    }


    @Operation(
            operationId = "resendAdminOutboxEntry",
            summary = "L67 — gửi lại một bản ghi outbox FAILED",
            description = """
                    Đưa dòng về `PENDING` với `next_attempt_at = now`; job đẩy outbox sẽ nhận ở \
                    lần chạy kế tiếp. Tìm ở CẢ `email_outbox` và `notification_outbox` (L66 trả \
                    danh sách đã hợp nhất hai bảng). Không có dòng ⇒ 404; dòng không ở trạng thái \
                    FAILED ⇒ 409. KHÔNG step-up, KHÔNG `reason` — p8 L67: "chỉ đẩy lại nội dung \
                    đã soạn". Trả 204.""")
    @PostMapping("/notifications/outbox/{id}/resend")
    public ResponseEntity<Void> resendOutboxEntry(
            @CurrentUser SecurityPrincipal principal,
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        AdminGuard.requireAnyRole(principal, SUPER_ONLY);
        AdminIdempotency.require(idempotencyKey);
        notificationOpsService.resendOutboxEntry(id, principal.userId(), actorRole(principal),
                request.getHeader("X-Request-Id"), request.getRemoteAddr(),
                request.getHeader("User-Agent"));
        return ResponseEntity.noContent().build();
    }


    @Operation(
            operationId = "listAdminSettings",
            summary = "L69 — bảng app_setting",
            description = """
                    Mọi khoá cấu hình runtime (p4 §H3). Giá trị của khoá `secret = true` BỊ CHE; \
                    tên khoá, kiểu, mô tả và mốc sửa vẫn hiện (admin phải thấy được "khoá này tồn \
                    tại và vừa bị đổi lúc nào"). Mỗi dòng mang `etag` riêng — L70 đòi `If-Match` \
                    theo từng khoá, nên một ETag chung cho cả danh sách là vô dụng.""")
    @GetMapping("/settings")
    public AppSettingListResponse settings(@CurrentUser SecurityPrincipal principal) {
        AdminGuard.requireAnyRole(principal, SUPER_ONLY);
        return AppSettingListResponse.of(settingsService.listMasked().stream()
                .map(row -> AppSettingResponse.from(row, AdminSettingsService.etagOf(row)))
                .toList());
    }


    @Operation(
            operationId = "updateAdminSetting",
            summary = "L70 — sửa một khoá cấu hình",
            description = """
                    `If-Match` BẮT BUỘC: thiếu ⇒ 428 PRECONDITION_REQUIRED, không khớp ⇒ \
                    412 RESOURCE_MODIFIED. Khoá không tồn tại ⇒ 404 SETTING_KEY_UNKNOWN — endpoint \
                    này KHÔNG tạo khoá mới (danh mục khoá thuộc p4 §H3 + seed). `reason` bắt buộc \
                    và `Idempotency-Key` bắt buộc. Giá trị được bọc theo `value_type` của chính \
                    dòng đó, nên gửi sai kiểu bị DB từ chối chứ không bị ghi biến dạng. Khoá \
                    `secret` trả về giá trị đã che, và `audit_log.before/after` cũng che (p4 §H3: \
                    che "trong UI và trong audit").""")
    @PatchMapping("/settings/{key}")
    public ResponseEntity<AppSettingResponse> updateSetting(
            @CurrentUser SecurityPrincipal principal,
            @PathVariable String key,
            @Valid @RequestBody UpdateAppSettingRequest body,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        AdminGuard.requireAnyRole(principal, SUPER_ONLY);
        String reason = AdminGuard.requireReason(body.reason());
        AdminIdempotency.require(idempotencyKey);
        AppSettingRow updated = settingsService.update(key, body.value(), ifMatch,
                principal.userId(), actorRole(principal), reason,
                request.getHeader("X-Request-Id"), request.getRemoteAddr(),
                request.getHeader("User-Agent"));
        String etag = AdminSettingsService.etagOf(updated);
        return ResponseEntity.ok()
                .eTag(etag)
                .body(AppSettingResponse.from(updated, etag));
    }


    @Operation(
            operationId = "broadcastAdminSystemNotification",
            summary = "L72 — gửi thông báo toàn hệ thống",
            description = """
                    `templateCode` = SYSTEM_MAINTENANCE | PRIVACY_INCIDENT_NOTICE (p8 L72); giá \
                    trị khác ⇒ 422 BROADCAST_TEMPLATE_NOT_ALLOWED. SYSTEM_MAINTENANCE: \
                    ADMIN_SUPER. PRIVACY_INCIDENT_NOTICE: ADMIN_SUPER hoặc DPO — p15 §15.9.5 \
                    REQ-INC-03 giao nghĩa vụ thông báo trong 72 GIỜ cho DPO, nên người chịu trách \
                    nhiệm pháp lý phải tự bấm được. `dryRun = true` chỉ ĐẾM người nhận và không \
                    ghi gì — dùng nó để kiểm chứng trước khi phát thật. Lần phát thật phải gửi \
                    `confirmed = true`, vì đây là endpoint duy nhất ghi một bản ghi cho MỌI user \
                    bằng một request.""")
    @PostMapping("/system/broadcast")
    public SystemBroadcastResponse broadcast(
            @CurrentUser SecurityPrincipal principal,
            @Valid @RequestBody SystemBroadcastRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        String templateCode = body.templateCode().strip();
        AdminGuard.requireAnyRole(principal, TEMPLATE_PRIVACY_INCIDENT.equals(templateCode)
                ? BROADCAST_INCIDENT_ROLES
                : SUPER_ONLY);
        String reason = AdminGuard.requireReason(body.reason());
        AdminIdempotency.require(idempotencyKey);
        if (!body.dryRunOrFalse() && !body.confirmedOrFalse()) {
            throw new BusinessRuleException(AdminOpsErrorCode.BROADCAST_CONFIRMATION_REQUIRED);
        }
        SystemBroadcastGateway.BroadcastResult result = notificationOpsService.broadcast(
                templateCode, body.title().strip(), body.body().strip(), body.payload(),
                body.dryRunOrFalse(), principal.userId(), actorRole(principal), reason,
                request.getHeader("X-Request-Id"), request.getRemoteAddr(),
                request.getHeader("User-Agent"));
        return SystemBroadcastResponse.from(templateCode, body.dryRunOrFalse(), result);
    }


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
