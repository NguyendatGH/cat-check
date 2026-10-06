package com.catcheck.privacy.api;

import com.catcheck.privacy.api.dto.RetentionPolicyResponse;
import com.catcheck.privacy.api.dto.AdminRetentionDryRunResponse;
import com.catcheck.privacy.api.dto.UpdateRetentionPolicyRequest;
import com.catcheck.privacy.application.RetentionService;
import com.catcheck.privacy.application.RequestEvidence;
import com.catcheck.privacy.domain.RetentionAction;
import com.catcheck.privacy.domain.RetentionPolicy;
import com.catcheck.privacy.domain.port.RetentionPolicyPort;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.security.AdminGuard;
import com.catcheck.shared.security.CurrentUser;
import com.catcheck.shared.security.SecurityPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/** L56/L57 — DPO đọc và cập nhật cấu hình retention, không đụng dữ liệu người dùng. */
@RestController
@RequestMapping("/api/v1/admin/privacy/retention-policies")
@Tag(name = "Quản trị retention", description = "Cấu hình thời hạn lưu dữ liệu")
public class AdminRetentionController {
    /** L57 {@code R:DPO} (REQ-RET-06: chỉ DPO sửa thời hạn — hệ quả pháp lý trực tiếp). */
    private static final Set<String> WRITE_ROLES = Set.of("DPO");
    /**
     * L56 {@code R:DPO,ADMIN_SUPER} — bản trước dùng {@code WRITE_ROLES} nên
     * {@code ADMIN_SUPER} <b>không đọc được</b> bảng retention dù p8 §8.4.12 ô L56 cho phép,
     * và L58 (dry-run, cũng cho ADMIN_SUPER) thì lại đọc được số liệu của chính bảng đó.
     */
    private static final Set<String> READ_ROLES = Set.of("DPO", "ADMIN_SUPER");
    /** L58 {@code R:ADMIN_SUPER,DPO} + p14 Q29. */
    private static final Set<String> DRY_RUN_ROLES = Set.of("DPO", "ADMIN_SUPER");

    private final RetentionService retentionService;
    private final RetentionPolicyPort policyPort;

    public AdminRetentionController(RetentionService retentionService, RetentionPolicyPort policyPort) {
        this.retentionService = retentionService;
        this.policyPort = policyPort;
    }

    @Operation(operationId = "listAdminRetentionPolicies", summary = "L56 — danh sách chính sách lưu trữ")
    @GetMapping
    public List<RetentionPolicyResponse> list(@CurrentUser SecurityPrincipal principal) {
        AdminGuard.requireAnyRole(principal, READ_ROLES);
        return retentionService.listPolicies().stream().map(RetentionPolicyResponse::from).toList();
    }

    @Operation(operationId = "updateAdminRetentionPolicy", summary = "L57 — cập nhật chính sách lưu trữ")
    @PatchMapping("/{code}")
    public RetentionPolicyResponse update(@CurrentUser SecurityPrincipal principal,
                                          @PathVariable String code,
                                          @RequestBody UpdateRetentionPolicyRequest request) {
        AdminGuard.requireAnyRole(principal, WRITE_ROLES);
        RetentionPolicy current = policyPort.findByCode(code)
                .orElseThrow(() -> new BusinessRuleException(PrivacyErrorCode.RETENTION_POLICY_NOT_FOUND, code));
        RetentionPolicy updated = merge(current, request, principal.userId());
        retentionService.savePolicy(updated, principal.userId());
        return RetentionPolicyResponse.from(updated);
    }

    @Operation(operationId = "dryRunRetentionPolicy", summary = "L58 — dry-run retention read-only")
    @org.springframework.web.bind.annotation.PostMapping("/{code}/dry-run")
    public AdminRetentionDryRunResponse dryRun(
            @CurrentUser SecurityPrincipal principal,
            @PathVariable String code,
            HttpServletRequest request) {
        AdminGuard.requireAnyRole(principal, DRY_RUN_ROLES);
        String role = principal.roles().stream()
                .map(value -> value.startsWith("ROLE_") ? value.substring("ROLE_".length()) : value)
                .filter(DRY_RUN_ROLES::contains)
                .findFirst()
                .orElse("ADMIN_SUPER");
        return AdminRetentionDryRunResponse.from(retentionService.dryRun(code, principal.userId(), role,
                RequestEvidence.of(request.getHeader("X-Request-Id"), request.getRemoteAddr(), request.getHeader("User-Agent"))));
    }

    private static RetentionPolicy merge(RetentionPolicy current, UpdateRetentionPolicyRequest request,
                                         java.util.UUID actor) {
        String targetTable = valueOrCurrent(request.targetTable(), current.targetTable(), "targetTable");
        String anchorColumn = valueOrCurrent(request.anchorColumn(), current.anchorColumn(), "anchorColumn");
        RetentionAction action = request.actionOnExpiry() == null
                ? current.actionOnExpiry() : request.actionOnExpiry();
        int threshold = request.safetyThresholdPercent() == null
                ? current.safetyThresholdPercent() : request.safetyThresholdPercent();
        if (threshold < 1 || threshold > 100) {
            throw new BusinessRuleException(PrivacyErrorCode.VALIDATION_FAILED, "safetyThresholdPercent");
        }
        return new RetentionPolicy(
                current.code(),
                request.dataInventoryCode() == null ? current.dataInventoryCode() : blankToNull(request.dataInventoryCode()),
                targetTable,
                request.retentionDays() == null ? current.retentionDays() : request.retentionDays(),
                anchorColumn,
                action,
                request.jobName() == null ? current.jobName() : blankToNull(request.jobName()),
                threshold,
                request.enabled() == null ? current.enabled() : request.enabled(),
                request.legalBasis() == null ? current.legalBasis() : blankToNull(request.legalBasis()),
                actor,
                current.createdAt());
    }

    private static String valueOrCurrent(String requested, String current, String field) {
        String value = requested == null ? current : requested.strip();
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException(PrivacyErrorCode.VALIDATION_FAILED, field);
        }
        return value;
    }

    private static String blankToNull(String value) {
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
