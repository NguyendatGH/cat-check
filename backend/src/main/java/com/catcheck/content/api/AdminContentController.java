package com.catcheck.content.api;

import com.catcheck.content.api.dto.CareTipDetailResponse;
import com.catcheck.content.api.dto.CreateCareTipRequest;
import com.catcheck.content.api.dto.OffsetPageResponse;
import com.catcheck.content.api.dto.PublishCareTipRequest;
import com.catcheck.content.api.dto.UpdateCareTipRequest;
import com.catcheck.content.application.CareTipAdminService;
import com.catcheck.content.application.CareTipAdminService.AdminPage;
import com.catcheck.content.application.ContentRoleGuard;
import com.catcheck.content.domain.CareTip;
import com.catcheck.content.domain.CareTipStatus;
import com.catcheck.shared.security.CurrentUser;
import com.catcheck.shared.security.SecurityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Quản trị nội dung — L40 đến L45.
 *
 * <p>Kiểm tra vai trò nằm trong controller (tầng api) chứ không ở service, vì đây là quyết định
 * "ai được gọi API này" — thuộc tầng web theo p8 §8.3. Nhưng tên vai trò cụ thể thì do
 * {@link ContentRoleGuard} giữ, để không rải chuỗi {@code "ADMIN_CATALOG"} khắp các controller.</p>
 *
 * <p>Chưa gắn {@code Aud} (ghi {@code audit_log}) và {@code S1:TOTP} vì {@code content} chỉ được
 * phụ thuộc module {@code audit} chứ chưa có cổng ghi, và bước xác thực hai lớp thuộc tầng web
 * dùng chung. Xem {@code docs/handovers/A3.md} mục "Còn lại".</p>
 */
@RestController
@RequestMapping("/api/v1/admin/care-tips")
@Tag(name = "Quản trị nội dung", description = "L40–L45 — soạn, duyệt, công bố nội dung")
public class AdminContentController {

    private final CareTipAdminService careTipAdminService;
    private final ContentRoleGuard contentRoleGuard;

    public AdminContentController(CareTipAdminService careTipAdminService, ContentRoleGuard contentRoleGuard) {
        this.careTipAdminService = careTipAdminService;
        this.contentRoleGuard = contentRoleGuard;
    }

    @Operation(
            operationId = "listAdminCareTips",
            summary = "Danh sách mọi trạng thái nội dung",
            description = "L40. Phân trang kiểu offset vì admin cần nhảy trang và tổng số dòng.")
    @GetMapping
    public OffsetPageResponse<CareTipDetailResponse> list(
            @CurrentUser SecurityPrincipal principal,
            @RequestParam(required = false) String locale,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        contentRoleGuard.requireContentAdmin(principal);
        // R4: enum domain không được nằm trong chữ ký controller.
        CareTipStatus statusFilter = EnumParam.parse(CareTipStatus.class, status, "status");
        AdminPage<CareTip> result = careTipAdminService.listForAdmin(locale, statusFilter, page, size);
        return new OffsetPageResponse<>(
                result.items().stream().map(CareTipDetailResponse::from).toList(),
                result.number(),
                result.size(),
                result.totalElements(),
                result.totalPages(),
                result.hasMore());
    }

    @Operation(operationId = "createCareTipDraft", summary = "Tạo bản nháp", description = "L41.")
    @PostMapping
    public CareTipDetailResponse create(
            @CurrentUser SecurityPrincipal principal,
            @Valid @RequestBody CreateCareTipRequest request) {

        contentRoleGuard.requireContentAdmin(principal);
        return CareTipDetailResponse.from(careTipAdminService.createDraft(
                request.slug(), request.locale(), request.kind(), request.category(),
                request.title(), request.claimType(), principal.userId()));
    }

    @Operation(
            operationId = "updateCareTipDraft",
            summary = "Sửa bản nháp",
            description = "L42. Trường vắng mặt nghĩa là giữ nguyên. Bài đang PUBLISHED trả "
                    + "409 CONTENT_NOT_EDITABLE.")
    @PatchMapping("/{tipId}")
    public CareTipDetailResponse update(
            @CurrentUser SecurityPrincipal principal,
            @PathVariable UUID tipId,
            @Valid @RequestBody UpdateCareTipRequest request) {

        contentRoleGuard.requireContentAdmin(principal);
        return CareTipDetailResponse.from(careTipAdminService.updateDraft(
                tipId, request.title(), request.summary(), request.bodyMd(),
                request.claimType(), request.sourceReference(), request.sortWeight()));
    }

    @Operation(operationId = "submitCareTipForReview", summary = "Gửi duyệt", description = "L43.")
    @PostMapping("/{tipId}/submit-review")
    public CareTipDetailResponse submitReview(
            @CurrentUser SecurityPrincipal principal,
            @PathVariable UUID tipId) {

        contentRoleGuard.requireContentAdmin(principal);
        return CareTipDetailResponse.from(careTipAdminService.submitReview(tipId));
    }

    @Operation(
            operationId = "publishCareTip",
            summary = "Công bố bài",
            description = "L44. 422 CONTENT_SOURCE_REQUIRED nếu claimType khác NONE mà thiếu nguồn; "
                    + "409 CONTENT_SELF_APPROVAL_FORBIDDEN nếu người soạn tự duyệt bài của mình.")
    @PostMapping("/{tipId}/publish")
    public CareTipDetailResponse publish(
            @CurrentUser SecurityPrincipal principal,
            @PathVariable UUID tipId,
            @Valid @RequestBody PublishCareTipRequest request) {

        contentRoleGuard.requirePublisher(principal);
        return CareTipDetailResponse.from(careTipAdminService.publish(tipId, principal.userId(), request.reason()));
    }

    @Operation(operationId = "archiveCareTip", summary = "Gỡ khỏi hiển thị", description = "L45.")
    @PostMapping("/{tipId}/archive")
    public CareTipDetailResponse archive(
            @CurrentUser SecurityPrincipal principal,
            @PathVariable UUID tipId) {

        contentRoleGuard.requireContentAdmin(principal);
        return CareTipDetailResponse.from(careTipAdminService.archive(tipId));
    }
}
