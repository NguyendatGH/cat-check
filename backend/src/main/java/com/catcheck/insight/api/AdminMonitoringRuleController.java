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

/**
 * L38, L39 — cấu hình rule cảnh báo (p8 §8.4.12 mục (c)).
 *
 * <p><b>Hai tập vai trò khác nhau, lấy đúng từ bảng p8:</b> L38 cho
 * {@code ADMIN_SUPER, ADMIN_CATALOG}; L39 <b>chỉ</b> {@code ADMIN_SUPER}. Hai ô này không có số
 * {@code Qxx} trong ma trận p14 §14.2.2 — p8 là part sở hữu danh mục endpoint nên code theo p8,
 * và ghi handoff H15.163 để p14 bổ sung dòng.</p>
 *
 * <p>Toàn bộ {@code /api/v1/admin/**} đã bị {@code AdminMfaGateFilter} chặn khi phiên chưa đạt
 * {@code mfaLevel = TOTP} (p8 §8.4.12 điều kiện chung #1), nên ở đây chỉ còn phần p8 cấp tới
 * từng endpoint — đúng tinh thần p14 §14.5.1 mục 2: UI ẩn nút, server vẫn kiểm độc lập.</p>
 */
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

    // ------------------------------------------------------------------ L38

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

    // ------------------------------------------------------------------ L39

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
        // Thứ tự có chủ ý: 404 (rule không tồn tại) TRƯỚC 428 (thiếu If-Match). Yêu cầu một
        // ETag cho tài nguyên không tồn tại là bắt client đoán, và 428 ở đây sẽ che mất lỗi
        // thật là sai `code`.
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

    /** Khoá ETag là {@code code} — khoá chính của {@code monitoring_rule} là khoá tự nhiên (p4 D11). */
    private static String etagOf(MonitoringRule rule) {
        return AdminETag.of(rule.getUpdatedAt(), rule.getCode());
    }

    /**
     * ETag của cả danh sách = băm các ETag dòng, để sửa bất kỳ rule nào cũng đổi ETag danh sách
     * (nền của {@code 304} ở lần GET sau).
     */
    private static String listEtag(List<AdminMonitoringRuleResponse> items) {
        int hash = items.stream().map(AdminMonitoringRuleResponse::etag).toList().hashCode();
        return "W/\"rules-" + Integer.toHexString(hash) + "\"";
    }
}
