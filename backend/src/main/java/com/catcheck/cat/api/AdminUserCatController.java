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

/**
 * Hồ sơ mèo ở màn quản trị — <b>L4</b> ({@code GET /admin/users/{userId}/cats}, p8 §8.4.12
 * mục (a)).
 *
 * <p><b>Vai trò</b> chép đúng cột {@code R:} của p8 L4, ánh xạ 1-1 sang ma trận p11 §11.5.4 (ô
 * Q3 của p14): {@code ADMIN_SUPER}/{@code ADMIN_SUPPORT} đọc <b>dữ liệu che</b>, {@code DPO}
 * đọc đầy đủ <b>chỉ khi</b> người dùng đó đang có {@code dsar_request} mở. p14 §14.5.1 mục 2:
 * UI ẩn nút với vai trò không có quyền, nhưng <b>server vẫn phải kiểm độc lập</b>.</p>
 *
 * <p><b>Chưa có:</b> step-up re-auth ({@code SW} của p8 — một lớp NGOÀI {@code mfaLevel} mà
 * {@code AdminMfaGateFilter} đã kiểm) và chế độ xem tạm thời tự đóng sau 15 phút mà p14 §14.3.2
 * mô tả. Handoff H15.102 (step-up) và H15.104 (cửa sổ 15 phút).</p>
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Quản trị hồ sơ mèo", description = "L4 — hồ sơ mèo của một người dùng (dữ liệu che)")
public class AdminUserCatController {

    /** p8 L4 / p11 §11.5.4 dòng "Hồ sơ mèo": Super + Support đọc dạng che, DPO đọc khi xử lý DSAR. */
    static final Set<String> READ_ROLES = Set.of("ADMIN_SUPER", "ADMIN_SUPPORT", "DPO");

    private final AdminCatViewService adminCatViewService;

    public AdminUserCatController(AdminCatViewService adminCatViewService) {
        this.adminCatViewService = adminCatViewService;
    }

    /**
     * {@code reason} là query param vì {@code GET} không có body — cùng đánh đổi đã ghi cho L12
     * và L22 (giá trị đi vào access log của proxy). Xem handoff H15.101.
     */
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

        // Dữ liệu của người KHÁC: không bao giờ vào cache nào — cùng lý do như L3 (p8 §8.1.10
        // mặc định `private, no-store` cho dữ liệu người dùng; ở đây nói rõ `no-store`).
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(body);
    }
}
