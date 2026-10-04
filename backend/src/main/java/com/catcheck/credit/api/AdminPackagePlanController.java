package com.catcheck.credit.api;

import com.catcheck.credit.api.dto.PackagePlanAdminResponse;
import com.catcheck.credit.api.dto.UpdatePackagePlanRequest;
import com.catcheck.credit.application.PackagePlanAdminService;
import com.catcheck.credit.domain.PackagePlanAdminView;
import com.catcheck.credit.domain.PackagePlanUpdate;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * Cấu hình gói — L25, L26 (p8 §8.4.12 mục (b)).
 *
 * <p><b>Vai trò ghi chỉ {@code ADMIN_SUPER}</b>, theo p11 §11.5.4 chứ không theo p8 L26 —
 * p11 sở hữu miền phân quyền và đã cắt {@code ADMIN_CATALOG} khỏi quyền sửa
 * {@code package_plan}. Đọc thì cả {@code ADMIN_CATALOG} và {@code ADMIN_SUPPORT} được
 * (p11 §11.5.4 dòng {@code package_plan}: Catalog <i>"Đọc, KHÔNG sửa"</i>, Support
 * <i>"Đọc"</i>). Xem handoff H15.100.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/package-plans")
@Tag(name = "Quản trị cấu hình gói", description = "L25–L26 — đọc và sửa package_plan")
public class AdminPackagePlanController {

    static final Set<String> READ_ROLES = Set.of("ADMIN_SUPER", "ADMIN_CATALOG", "ADMIN_SUPPORT");

    static final Set<String> WRITE_ROLES = Set.of("ADMIN_SUPER");

    private final PackagePlanAdminService adminService;

    public AdminPackagePlanController(PackagePlanAdminService adminService) {
        this.adminService = adminService;
    }

    // ------------------------------------------------------------------ L25

    @Operation(
            operationId = "listAdminPackagePlans",
            summary = "L25 — cấu hình các gói",
            description = "Mặc định chỉ gói đang bán; `includeInactive=true` để xem cả gói ngừng bán. "
                    + "Mỗi dòng kèm `etag` để L26 dùng làm `If-Match`.")
    @GetMapping
    public List<PackagePlanAdminResponse> list(
            @CurrentUser SecurityPrincipal principal,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        AdminGuard.requireAnyRole(principal, READ_ROLES);
        return adminService.list(includeInactive).stream()
                .map(view -> PackagePlanAdminResponse.from(view, etagOf(view)))
                .toList();
    }

    // ------------------------------------------------------------------ L26

    @Operation(
            operationId = "updateAdminPackagePlan",
            summary = "L26 — sửa cấu hình một gói",
            description = "`If-Match` bắt buộc (thiếu ⇒ 428, lệch ⇒ 412). `reason` bắt buộc ≥ 10 ký tự. "
                    + "Mỗi lần ghi tăng `version`; thay đổi KHÔNG hồi tố các lô credit đã kích hoạt "
                    + "(p5 R1).")
    @PatchMapping("/{code}")
    public ResponseEntity<PackagePlanAdminResponse> update(
            @CurrentUser SecurityPrincipal principal,
            @PathVariable String code,
            @RequestHeader(name = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody UpdatePackagePlanRequest request,
            HttpServletRequest httpRequest) {
        AdminGuard.requireAnyRole(principal, WRITE_ROLES);
        PackagePlanAdminView current = adminService.require(code);
        AdminETag.requireMatch(ifMatch, etagOf(current));

        PackagePlanUpdate update = new PackagePlanUpdate(
                request.creditAmount(),
                request.creditValidityDays(),
                request.maxCatProfiles(),
                request.features() == null ? null : request.features().toDomain(),
                request.active(),
                request.clearMaxCatProfilesFlag());
        PackagePlanAdminView after = adminService.update(
                code, update, AdminContext.of(principal, request.reason(), httpRequest));
        String etag = etagOf(after);
        return ResponseEntity.ok().eTag(etag).body(PackagePlanAdminResponse.from(after, etag));
    }

    /** Khoá ETag là {@code code} — khoá chính của {@code package_plan} là khoá tự nhiên (p4 §4.1.1). */
    private static String etagOf(PackagePlanAdminView view) {
        return AdminETag.of(view.updatedAt(), view.code());
    }
}
