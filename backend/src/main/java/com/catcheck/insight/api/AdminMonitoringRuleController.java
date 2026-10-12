package com.catcheck.insight.api;

import com.catcheck.insight.api.dto.AdminMonitoringRuleListResponse;
import com.catcheck.insight.api.dto.AdminMonitoringRuleResponse;
import com.catcheck.insight.api.dto.UpdateMonitoringRuleRequest;
import com.catcheck.insight.application.MonitoringRuleAdminService;
import com.catcheck.insight.domain.MonitoringRule;
import com.catcheck.shared.api.AdminETag;
import com.catcheck.shared.security.AdminGuard;
import com.catcheck.shared.security.CurrentUser;
import com.catcheck.shared.security.SecurityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin/monitoring-rules")
@Tag(name = "Quản trị rule cảnh báo", description = "L38–L39 — đọc và sửa monitoring_rule")
public class AdminMonitoringRuleController {

    static final Set<String> READ_ROLES = Set.of("ADMIN_SUPER", "ADMIN_CATALOG");

    static final Set<String> WRITE_ROLES = Set.of("ADMIN_SUPER");

    private final MonitoringRuleAdminService adminService;

    public AdminMonitoringRuleController(MonitoringRuleAdminService adminService) {
        this.adminService = adminService;
    }


    @Operation(
            operationId = "listAdminMonitoringRules",
            summary = "L38 — cấu hình rule cảnh báo",
            description = "Mọi rule kể cả rule đang tắt. Mỗi dòng kèm `etag` để L39 dùng làm "
                    + "`If-Match`; header `ETag` là ETag tổng hợp của cả danh sách.")
    @GetMapping
    public ResponseEntity<AdminMonitoringRuleListResponse> list(
            @CurrentUser SecurityPrincipal principal) {
        AdminGuard.requireAnyRole(principal, READ_ROLES);
        List<MonitoringRule> rules = adminService.listForAdmin();
        List<AdminMonitoringRuleResponse> items = rules.stream()
                .map(rule -> AdminMonitoringRuleResponse.from(rule, etagOf(rule)))
                .toList();
        return ResponseEntity.ok()
                .eTag(listEtag(items))
                .body(new AdminMonitoringRuleListResponse(items));
    }


    @Operation(
            operationId = "updateAdminMonitoringRule",
            summary = "L39 — bật/tắt, đổi tham số & cooldown",
            description = "`If-Match` bắt buộc (thiếu ⇒ 428 PRECONDITION_REQUIRED, lệch ⇒ 412 "
                    + "RESOURCE_MODIFIED). `reason` bắt buộc ≥ 10 ký tự. `cooldownHours = 0` chỉ "
                    + "hợp lệ cho URGENT_CLINICAL_SIGN ⇒ 422 MONITORING_RULE_INVALID.")
    @PatchMapping("/{code}")
    public ResponseEntity<AdminMonitoringRuleResponse> update(
            @CurrentUser SecurityPrincipal principal,
            @PathVariable String code,
            @RequestHeader(name = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody UpdateMonitoringRuleRequest request,
            HttpServletRequest httpRequest) {
        AdminGuard.requireAnyRole(principal, WRITE_ROLES);
        MonitoringRule current = adminService.require(code);
        AdminETag.requireMatch(ifMatch, etagOf(current));
        MonitoringRule updated = adminService.update(
                code,
                request.enabled(),
                request.params(),
                request.cooldownHours(),
                request.pushEnabled(),
                new MonitoringRuleAdminService.RuleAdminAction(
                        principal.userId(),
                        AdminGuard.requireReason(request.reason()),
                        httpRequest.getHeader("X-Request-Id"),
                        httpRequest.getRemoteAddr(),
                        httpRequest.getHeader("User-Agent")));
        String etag = etagOf(updated);
        return ResponseEntity.ok().eTag(etag).body(AdminMonitoringRuleResponse.from(updated, etag));
    }

    private static String etagOf(MonitoringRule rule) {
        return AdminETag.of(rule.getUpdatedAt(), rule.getCode());
    }

    private static String listEtag(List<AdminMonitoringRuleResponse> items) {
        int hash = items.stream().map(AdminMonitoringRuleResponse::etag).toList().hashCode();
        return "W/\"rules-" + Integer.toHexString(hash) + "\"";
    }
}
