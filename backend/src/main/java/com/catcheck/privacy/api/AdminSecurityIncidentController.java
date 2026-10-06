package com.catcheck.privacy.api;

import com.catcheck.privacy.api.dto.AdminSecurityIncidentResponse;
import com.catcheck.privacy.api.dto.OpenSecurityIncidentRequest;
import com.catcheck.privacy.api.dto.UpdateSecurityIncidentRequest;
import com.catcheck.privacy.application.RequestEvidence;
import com.catcheck.privacy.application.SecurityIncidentService;
import com.catcheck.privacy.domain.IncidentCategory;
import com.catcheck.privacy.domain.IncidentSeverity;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.security.AdminGuard;
import com.catcheck.shared.security.CurrentUser;
import com.catcheck.shared.security.SecurityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * L59/L60/L61 — hồ sơ sự cố lộ/mất dữ liệu ({@code security_incident}, p4 B9,
 * quy trình p15 §15.9.5).
 *
 * <p><b>Hai mức quyền khác nhau trong một lớp</b>, đúng ô {@code Q27} của p14 §14.2.2
 * (<i>{@code DPO} đọc + ghi, {@code ADMIN_SUPER} 👁</i>) và cột Auth của p8 L59
 * (<i>{@code R:DPO} (ghi), {@code ADMIN_SUPER} (đọc)</i>): {@code GET} cho cả hai,
 * {@code POST}/{@code PATCH} chỉ {@code DPO}. Đây là lý do {@code READ_ROLES} và
 * {@code WRITE_ROLES} là hai tập, không phải một.</p>
 *
 * <p><b>Không có endpoint xoá</b>, cố ý: {@code security_incident} bị REVOKE DELETE ở tầng
 * DB (V6) vì hồ sơ phải giữ ≥ 05 năm kể từ ngày khắc phục (Đ29.1.c NĐ356, p15 §15.5.1 dòng
 * "Hồ sơ sự cố": <i>"không tự xoá — rà soát thủ công hằng năm"</i>).</p>
 */
@RestController
@RequestMapping("/api/v1/admin/security/incidents")
@Tag(name = "Quản trị sự cố bảo mật", description = "Hồ sơ sự cố lộ/mất dữ liệu cá nhân")
public class AdminSecurityIncidentController {

    /** p8 L59: {@code ADMIN_SUPER} đọc, {@code DPO} đọc + ghi. */
    private static final Set<String> READ_ROLES = Set.of("DPO", "ADMIN_SUPER");
    /** p8 L60/L61 + p14 Q27: chỉ {@code DPO} ghi. */
    private static final Set<String> WRITE_ROLES = Set.of("DPO");

    private static final int MAX_PAGE_SIZE = 100;

    private final SecurityIncidentService incidentService;
    private final Clock clock;

    public AdminSecurityIncidentController(SecurityIncidentService incidentService, Clock clock) {
        this.incidentService = incidentService;
        this.clock = clock;
    }

    @Operation(operationId = "listAdminSecurityIncidents", summary = "L59 — hàng chờ hồ sơ sự cố")
    @GetMapping
    public PageResponse list(@CurrentUser SecurityPrincipal principal,
                             @RequestParam(required = false) String severity,
                             @RequestParam(required = false) Boolean unresolved,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "20") int size) {
        AdminGuard.requireAnyRole(principal, READ_ROLES);
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        IncidentSeverity parsedSeverity = parseSeverity(severity, false);
        Instant now = clock.instant();
        List<AdminSecurityIncidentResponse> items = incidentService
                .list(parsedSeverity, unresolved, safePage * safeSize, safeSize).stream()
                .map(incident -> AdminSecurityIncidentResponse.from(incident, now))
                .toList();
        return PageResponse.of(items, safePage, safeSize,
                incidentService.count(parsedSeverity, unresolved));
    }

    @Operation(operationId = "getAdminSecurityIncident", summary = "Chi tiết một hồ sơ sự cố")
    @GetMapping("/{incidentId}")
    public AdminSecurityIncidentResponse detail(@CurrentUser SecurityPrincipal principal,
                                                @PathVariable UUID incidentId) {
        AdminGuard.requireAnyRole(principal, READ_ROLES);
        return AdminSecurityIncidentResponse.from(incidentService.get(incidentId), clock.instant());
    }

    @Operation(operationId = "openAdminSecurityIncident", summary = "L60 — mở hồ sơ sự cố")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminSecurityIncidentResponse open(@CurrentUser SecurityPrincipal principal,
                                              @Valid @RequestBody OpenSecurityIncidentRequest body,
                                              HttpServletRequest request) {
        AdminGuard.requireAnyRole(principal, WRITE_ROLES);
        String reason = AdminGuard.requireReason(body.reason());
        SecurityIncidentService.Command command = new SecurityIncidentService.Command(
                parseSeverity(body.severity(), true),
                parseCategory(body.category()),
                body.summary(),
                body.affectedSubjectCount(),
                body.affectedDataCodes(),
                body.detectedAt(),
                body.reportRef(),
                reason);
        return AdminSecurityIncidentResponse.from(
                incidentService.open(principal.userId(), command, evidence(request)), clock.instant());
    }

    @Operation(operationId = "updateAdminSecurityIncident",
            summary = "L61 — phân loại, mốc thông báo 72 giờ, kết luận")
    @PatchMapping("/{incidentId}")
    public AdminSecurityIncidentResponse update(@CurrentUser SecurityPrincipal principal,
                                                @PathVariable UUID incidentId,
                                                @Valid @RequestBody UpdateSecurityIncidentRequest body,
                                                HttpServletRequest request) {
        AdminGuard.requireAnyRole(principal, WRITE_ROLES);
        String reason = AdminGuard.requireReason(body.reason());
        SecurityIncidentService.Patch patch = new SecurityIncidentService.Patch(
                parseSeverity(body.severity(), false),
                body.category() == null || body.category().isBlank() ? null : parseCategory(body.category()),
                body.summary(),
                body.affectedSubjectCount(),
                body.affectedDataCodes(),
                body.classifiedAt(),
                body.containedAt(),
                body.authorityNotifiedAt(),
                body.subjectsNotifiedAt(),
                body.resolvedAt(),
                body.reportRef(),
                reason);
        return AdminSecurityIncidentResponse.from(
                incidentService.patch(principal.userId(), incidentId, patch, evidence(request)),
                clock.instant());
    }

    private static IncidentSeverity parseSeverity(String value, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) {
                throw new BusinessRuleException(PrivacyErrorCode.VALIDATION_FAILED, "severity");
            }
            return null;
        }
        try {
            return IncidentSeverity.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException(PrivacyErrorCode.VALIDATION_FAILED, "severity");
        }
    }

    private static IncidentCategory parseCategory(String value) {
        try {
            return IncidentCategory.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new BusinessRuleException(PrivacyErrorCode.VALIDATION_FAILED, "category");
        }
    }

    private static RequestEvidence evidence(HttpServletRequest request) {
        return RequestEvidence.of(request.getHeader("X-Request-Id"), request.getRemoteAddr(),
                request.getHeader("User-Agent"));
    }

    /** Envelope offset theo cột "Trang = {@code O}" của ô L59 (p8 §8.1.4). */
    public record PageResponse(List<AdminSecurityIncidentResponse> items, int page, int size,
                               long totalElements, int totalPages, boolean hasMore) {

        static PageResponse of(List<AdminSecurityIncidentResponse> items, int page, int size, long total) {
            int pages = size <= 0 ? 0 : (int) Math.ceilDiv(total, size);
            return new PageResponse(items, page, size, total, pages, (long) (page + 1) * size < total);
        }
    }
}
