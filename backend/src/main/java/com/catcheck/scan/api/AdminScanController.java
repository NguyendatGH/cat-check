package com.catcheck.scan.api;

import com.catcheck.scan.api.dto.AdminReassignCatRequest;
import com.catcheck.scan.api.dto.AdminReassignCatResponse;
import com.catcheck.scan.api.dto.AdminScanItemResponse;
import com.catcheck.scan.api.dto.AdminScanListResponse;
import com.catcheck.scan.application.AdminActionContext;
import com.catcheck.scan.application.AdminScanService;
import com.catcheck.scan.application.ScanImageAccessService;
import com.catcheck.scan.application.ScanQueryService;
import com.catcheck.shared.security.AdminGuard;
import com.catcheck.shared.security.CurrentUser;
import com.catcheck.shared.security.SecurityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Dữ liệu quét ở màn quản trị — <b>L5</b> ({@code GET /admin/users/{userId}/scans}), <b>L6</b>
 * ({@code GET /admin/scans/{scanId}/image}) và <b>L18</b>
 * ({@code POST /admin/scans/{scanId}/reassign-cat}), p8 §8.4.12 mục (a).
 *
 * <p><b>Vai trò</b> chép đúng cột {@code R:} của p8, ánh xạ 1-1 sang ma trận p11 §11.5.4:
 * L5 đọc cho {@code ADMIN_SUPER}/{@code ADMIN_SUPPORT}/{@code DPO} (ô Q4); L6 <b>chỉ
 * {@code DPO}</b> (ô Q5 — <i>"vai trò duy nhất"</i>); L18 cho
 * {@code ADMIN_SUPPORT}/{@code ADMIN_SUPER}. p14 §14.5.1 mục 2: UI ẩn nút với vai trò không có
 * quyền, nhưng <b>server vẫn phải kiểm độc lập</b> — đó là việc của {@code AdminGuard} ở đây,
 * bộ gác chuỗi filter chỉ biết "có phải nhóm admin không".</p>
 *
 * <p><b>Chưa có:</b> step-up re-auth ({@code SW} của p8 cho L5/L6 — một lớp NGOÀI
 * {@code mfaLevel} mà {@code AdminMfaGateFilter} đã kiểm) và chế độ xem tạm thời tự đóng sau 15
 * phút mà p14 §14.3.2 mô tả. Handoff H15.102 (step-up) và H15.104 (cửa sổ 15 phút).</p>
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Quản trị dữ liệu quét", description = "L5, L6, L18 — số liệu scan, ảnh scan (DSAR), gán lại mèo")
public class AdminScanController {

    /** p8 L5 / p11 §11.5.4 "Kết quả scan": Super và Support đọc số liệu, DPO đọc khi xử lý DSAR. */
    static final Set<String> SCAN_READ_ROLES = Set.of("ADMIN_SUPER", "ADMIN_SUPPORT", "DPO");

    /** p8 L6 / p14 ô Q5: ảnh scan là của {@code DPO} và chỉ DPO. */
    static final Set<String> IMAGE_ROLES = Set.of("DPO");

    /** p8 L18: gán lại mèo sau cửa sổ 24 giờ là việc của tổng đài. */
    static final Set<String> REASSIGN_ROLES = Set.of("ADMIN_SUPPORT", "ADMIN_SUPER");

    private static final String SHARED_UNKNOWN = "SHARED_UNKNOWN";

    private final AdminScanService adminScanService;
    private final ScanQueryService queryService;

    public AdminScanController(AdminScanService adminScanService, ScanQueryService queryService) {
        this.adminScanService = adminScanService;
        this.queryService = queryService;
    }

    // ------------------------------------------------------------------- L5

    /**
     * {@code reason} là query param vì {@code GET} không có body — cùng đánh đổi đã ghi cho L12
     * và L22 (giá trị đi vào access log của proxy). Xem handoff H15.101.
     */
    @Operation(
            operationId = "listAdminUserScans",
            summary = "L5 — số liệu quét của một người dùng",
            description = "`phValue`, phân loại, Lab. KHÔNG có trường nào trỏ tới ảnh ở bất kỳ vai "
                    + "trò nào (p11 §11.5.4) — ảnh chỉ qua L6. Văn bản tự do bị che, trừ khi người "
                    + "gọi là DPO và người dùng đang có `dsar_request` mở. Phân trang offset.")
    @GetMapping("/users/{userId}/scans")
    public AdminScanListResponse listUserScans(
            @CurrentUser SecurityPrincipal principal,
            @PathVariable UUID userId,
            @RequestParam String reason,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        AdminGuard.requireAnyRole(principal, SCAN_READ_ROLES);
        AdminActionContext context = AdminScanContext.withReason(principal, reason, httpRequest);
        AdminScanService.AdminScanPage result =
                adminScanService.listUserScans(userId, page, size, context);

        // Một lần tra tên cho mỗi mèo KHÁC NHAU, không mỗi dòng: một trang 100 scan của nhà hai
        // mèo chỉ cần hai câu, không phải 100 (N+1 nhìn thấy được ngay trên màn hỗ trợ).
        Map<UUID, String> catNames = new HashMap<>();
        List<AdminScanItemResponse> items = result.rows().stream()
                .map(row -> AdminScanItemResponse.from(
                        row,
                        row.catId() == null ? null
                                : catNames.computeIfAbsent(row.catId(), queryService::catName),
                        result.fullData()))
                .toList();

        return AdminScanListResponse.of(
                items, result.page(), result.size(), result.totalElements(), !result.fullData());
    }

    // ------------------------------------------------------------------- L6

    @Operation(
            operationId = "getAdminScanImage",
            summary = "L6 — ảnh gốc của một lần quét (chỉ DPO, chỉ khi có DSAR mở)",
            description = "403 SCAN_IMAGE_DSAR_REQUIRED khi người dùng đó không có `dsar_request` "
                    + "nào đang mở; 404 SCAN_IMAGE_NOT_STORED khi `store_image = false`; "
                    + "410 SCAN_IMAGE_EXPIRED sau 14 ngày. Cả lần bị từ chối cũng vào `audit_log`.")
    @GetMapping("/scans/{scanId}/image")
    public ResponseEntity<InputStreamResource> getScanImage(
            @CurrentUser SecurityPrincipal principal,
            @PathVariable UUID scanId,
            @RequestParam String reason,
            HttpServletRequest httpRequest) {
        AdminGuard.requireAnyRole(principal, IMAGE_ROLES);
        ScanImageAccessService.Result result = adminScanService.openImageForDpo(
                scanId, AdminScanContext.withReason(principal, reason, httpRequest));

        if (result instanceof ScanImageAccessService.Redirect redirect) {
            return ResponseEntity.status(302).location(redirect.location()).build();
        }
        ScanImageAccessService.Stream stream = (ScanImageAccessService.Stream) result;
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(stream.contentType()))
                .contentLength(stream.bytes())
                // `no-store`, KHÔNG `private, max-age=300` như E7: đây là ảnh của người KHÁC, mở
                // dưới một cơ sở pháp lý có thời hạn. Để nó nằm trong cache trình duyệt của admin
                // là giữ lại quyền truy cập sau khi quyền đã hết.
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new InputStreamResource(stream.content()));
    }

    // ------------------------------------------------------------------ L18

    @Operation(
            operationId = "reassignAdminScanCat",
            summary = "L18 — gán lại lần quét cho mèo khác (sau cửa sổ 24 giờ của người dùng)",
            description = "Không áp cửa sổ 24 giờ và không áp trần 3 lần của p6 §6.10.3 — xem "
                    + "handoff H15.152. Mèo đích phải thuộc CHỦ CỦA LẦN QUÉT, không phải admin; "
                    + "không thuộc ⇒ 404 CAT_NOT_FOUND (p8 §8.2.5 mục 1 — không xác nhận id của "
                    + "người khác). `reason` bắt buộc ≥ 10 ký tự.")
    @PostMapping("/scans/{scanId}/reassign-cat")
    public AdminReassignCatResponse reassignScanCat(
            @CurrentUser SecurityPrincipal principal,
            @PathVariable UUID scanId,
            @Valid @RequestBody AdminReassignCatRequest request,
            HttpServletRequest httpRequest) {
        AdminGuard.requireAnyRole(principal, REASSIGN_ROLES);
        boolean toShared = SHARED_UNKNOWN.equals(request.toAssignment());
        UUID toCatId = toShared || request.toCatId() == null
                ? null : parseCatId(request.toCatId());

        AdminScanService.AdminReassignResult result = adminScanService.reassign(
                scanId, toCatId, toShared,
                AdminScanContext.withReason(principal, request.reason(), httpRequest));

        return new AdminReassignCatResponse(
                result.scanId().toString(),
                result.fromCatId() == null ? null : result.fromCatId().toString(),
                result.toCatId() == null ? null : result.toCatId().toString(),
                result.toAssignment(),
                result.reassignCount());
    }

    /* ---------------------------------------------------------------- helper */

    /**
     * UUID sai định dạng ⇒ {@code 400 VALIDATION_FAILED} chứ không phải 500: {@code toCatId} là
     * chuỗi trong DTO (hợp đồng E9 cũng vậy), nên Spring không tự chuyển kiểu hộ.
     */
    private static UUID parseCatId(String value) {
        try {
            return UUID.fromString(value.strip());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("toCatId không phải UUID hợp lệ");
        }
    }
}
