package com.catcheck.cat.api;

import com.catcheck.cat.api.dto.AdminCatListResponse;
import com.catcheck.cat.api.dto.AdminCatResponse;
import com.catcheck.cat.application.AdminCatViewService;
import com.catcheck.shared.security.AdminGuard;
import com.catcheck.shared.security.CurrentUser;
import com.catcheck.shared.security.SecurityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Quản trị hồ sơ mèo", description = "L4 — hồ sơ mèo của một người dùng (dữ liệu che)")
public class AdminUserCatController {

    static final Set<String> READ_ROLES = Set.of("ADMIN_SUPER", "ADMIN_SUPPORT", "DPO");

    private final AdminCatViewService adminCatViewService;

    public AdminUserCatController(AdminCatViewService adminCatViewService) {
        this.adminCatViewService = adminCatViewService;
    }

    @Operation(
            operationId = "listAdminUserCats",
            summary = "L4 — hồ sơ mèo của một người dùng",
            description = "Văn bản tự do (tên bé, giống tự nhập, ghi chú) bị che; DPO thấy đầy đủ "
                    + "khi người dùng đang có `dsar_request` mở (p8 L4). Không trả `storage_key` "
                    + "của ảnh đại diện, chỉ cờ `hasAvatar`. `reason` bắt buộc ≥ 10 ký tự.")
    @GetMapping("/{userId}/cats")
    public ResponseEntity<AdminCatListResponse> listCats(
            @CurrentUser SecurityPrincipal principal,
            @PathVariable UUID userId,
            @RequestParam String reason,
            HttpServletRequest httpRequest) {
        AdminGuard.requireAnyRole(principal, READ_ROLES);
        AdminCatViewService.AdminCatListing listing = adminCatViewService.listCatsOf(
                userId, AdminCatContext.withReason(principal, reason, httpRequest));

        AdminCatListResponse body = AdminCatListResponse.of(
                listing.cats().stream()
                        .map(cat -> AdminCatResponse.from(cat, listing.fullData()))
                        .toList(),
                !listing.fullData());

        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(body);
    }
}
