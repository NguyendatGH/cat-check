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

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Quản trị dữ liệu quét", description = "L5, L6, L18 — số liệu scan, ảnh scan (DSAR), gán lại mèo")
public class AdminScanController {

    static final Set<String> SCAN_READ_ROLES = Set.of("ADMIN_SUPER", "ADMIN_SUPPORT", "DPO");

    static final Set<String> IMAGE_ROLES = Set.of("DPO");

    static final Set<String> REASSIGN_ROLES = Set.of("ADMIN_SUPPORT", "ADMIN_SUPER");

    private static final String SHARED_UNKNOWN = "SHARED_UNKNOWN";

    private final AdminScanService adminScanService;
    private final ScanQueryService queryService;

    public AdminScanController(AdminScanService adminScanService, ScanQueryService queryService) {
        this.adminScanService = adminScanService;
        this.queryService = queryService;
    }


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
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new InputStreamResource(stream.content()));
    }


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


    private static UUID parseCatId(String value) {
        try {
            return UUID.fromString(value.strip());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("toCatId không phải UUID hợp lệ");
        }
    }
}
