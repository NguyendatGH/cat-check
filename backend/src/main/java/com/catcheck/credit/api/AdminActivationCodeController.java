package com.catcheck.credit.api;

import com.catcheck.credit.api.dto.ActivationBatchResponse;
import com.catcheck.credit.api.dto.ActivationCodeAdminResponse;
import com.catcheck.credit.api.dto.AdminPageResponse;
import com.catcheck.credit.api.dto.AdminReasonRequest;
import com.catcheck.credit.api.dto.IssueActivationBatchRequest;
import com.catcheck.credit.api.dto.IssuedActivationBatchResponse;
import com.catcheck.credit.application.ActivationCodeAdminService;
import com.catcheck.credit.domain.ActivationBatchSummary;
import com.catcheck.credit.domain.ActivationCode;
import com.catcheck.credit.domain.ActivationCodeFilter;
import com.catcheck.credit.domain.ActivationCodeStatus;
import com.catcheck.shared.security.AdminGuard;
import com.catcheck.shared.security.CurrentUser;
import com.catcheck.shared.security.SecurityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

/**
 * Quản trị mã kích hoạt — L19–L24 (p8 §8.4.12 mục (b)).
 *
 * <p>Điều kiện chung của {@code /api/v1/admin/**} (phiên {@code mfaLevel = TOTP}) do
 * {@code shared.security.AdminAccessGate} lo, đặt trong chuỗi filter — không lặp lại ở đây.
 * Phần controller kiểm là <b>cột {@code R:} của từng endpoint</b>: L19/L21 cho
 * {@code ADMIN_SUPER} + {@code ADMIN_SUPPORT} (tổng đài tra cứu trạng thái một mã), còn mọi
 * hành động ghi chỉ {@code ADMIN_SUPER} (p11 §11.5.4 dòng "Mã kích hoạt": Support chỉ
 * <i>"tra cứu theo prefix"</i>, Super <i>"sinh lô, huỷ mã"</i>).</p>
 *
 * <p><b>Chưa có:</b> step-up re-auth ({@code S1:TOTP} của p8 — một lớp NGOÀI {@code mfaLevel},
 * đòi nhập lại mã trong 5 phút gần nhất) và {@code Idempotency-Key}. Cả hai là filter dùng
 * chung chưa viết; xem handoff H15.102.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/activation-codes")
@Tag(name = "Quản trị mã kích hoạt", description = "L19–L24 — sinh lô, tải CSV một lần, void mã/lô")
public class AdminActivationCodeController {

    /** p11 §11.5.4: Support được tra cứu, Super được tra cứu + ghi. */
    static final Set<String> READ_ROLES = Set.of("ADMIN_SUPER", "ADMIN_SUPPORT");

    /** p11 §11.5.4 + p14 §14.3.2 mục 4: sinh lô / tải CSV / void là {@code ADMIN_SUPER} duy nhất. */
    static final Set<String> WRITE_ROLES = Set.of("ADMIN_SUPER");

    private static final int MAX_PAGE_SIZE = 200;

    private final ActivationCodeAdminService adminService;

    public AdminActivationCodeController(ActivationCodeAdminService adminService) {
        this.adminService = adminService;
    }

    // ------------------------------------------------------------------ L19

    @Operation(
            operationId = "listAdminActivationCodes",
            summary = "L19 — tra cứu mã kích hoạt",
            description = "Lọc theo prefix/gói/trạng thái/lô. KHÔNG tra theo mã đầy đủ: DB chỉ có "
                    + "`code_hash` (p8 §8.4.12). Phân trang offset.")
    @GetMapping
    public AdminPageResponse<ActivationCodeAdminResponse> list(
            @CurrentUser SecurityPrincipal principal,
            @RequestParam(required = false) String prefix,
            @RequestParam(required = false) String packageCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String batchId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        AdminGuard.requireAnyRole(principal, READ_ROLES);
        ActivationCodeFilter filter = new ActivationCodeFilter(
                prefix, packageCode, parseStatus(status), batchId);
        ActivationCodeAdminService.Page<ActivationCode> result =
                adminService.search(filter, normalizePage(page), normalizeSize(size));
        return AdminPageResponse.of(
                result.items().stream().map(ActivationCodeAdminResponse::from).toList(),
                normalizePage(page), normalizeSize(size), result.totalElements());
    }

    // ------------------------------------------------------------------ L20

    @Operation(
            operationId = "issueAdminActivationBatch",
            summary = "L20 — sinh lô mã",
            description = "≤ 50 000 mã/lần, vượt ⇒ 422 ACTIVATION_BATCH_TOO_LARGE. `productionBatch` "
                    + "là định danh lô và phải chưa tồn tại (409 ACTIVATION_BATCH_EXISTS). Mã thô "
                    + "KHÔNG nằm trong response — tải qua L22 đúng một lần.")
    @PostMapping("/batch")
    public ResponseEntity<IssuedActivationBatchResponse> issueBatch(
            @CurrentUser SecurityPrincipal principal,
            @Valid @RequestBody IssueActivationBatchRequest request,
            HttpServletRequest httpRequest) {
        AdminGuard.requireAnyRole(principal, WRITE_ROLES);
        ActivationBatchSummary batch = adminService.issueBatch(
                request.packageCode(), request.quantity(), request.productionBatch(),
                request.validForDays(), AdminContext.of(principal, request.reason(), httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(new IssuedActivationBatchResponse(
                batch.productionBatch(),
                batch.productionBatch(),
                batch.packageCode(),
                (int) batch.totalCodes(),
                batch.issuedAt(),
                batch.validUntil(),
                true,
                csvPath(batch.productionBatch())));
    }

    // ------------------------------------------------------------------ L21

    @Operation(
            operationId = "listAdminActivationBatches",
            summary = "L21 — danh sách lô đã phát hành",
            description = "Gom theo `production_batch`. `csvAvailable = false` nghĩa là mã thô "
                    + "không còn lấy lại được (đã tải, hoặc backend đã khởi động lại).")
    @GetMapping("/batches")
    public AdminPageResponse<ActivationBatchResponse> listBatches(
            @CurrentUser SecurityPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        AdminGuard.requireAnyRole(principal, READ_ROLES);
        ActivationCodeAdminService.Page<ActivationBatchSummary> result =
                adminService.listBatches(normalizePage(page), normalizeSize(size));
        return AdminPageResponse.of(
                result.items().stream()
                        .map(batch -> ActivationBatchResponse.from(
                                batch, adminService.csvAvailable(batch.productionBatch())))
                        .toList(),
                normalizePage(page), normalizeSize(size), result.totalElements());
    }

    // ------------------------------------------------------------------ L22

    /**
     * {@code reason} là <b>query param</b>, không phải body: đây là {@code GET} và một
     * {@code GET} có body thì nhiều proxy/thư viện HTTP lặng lẽ bỏ qua. Đánh đổi đã biết:
     * {@code reason} xuất hiện trong access log của reverse proxy — p16 §16.6.7 cấm gửi query
     * string vào log ứng dụng, còn log của proxy thuộc cấu hình hạ tầng (p18). Ghi handoff
     * H15.101.
     */
    @Operation(
            operationId = "downloadAdminActivationBatchCsv",
            summary = "L22 — tải CSV mã thô (một lần duy nhất)",
            description = "Lần hai ⇒ 410 ACTIVATION_CSV_ALREADY_DOWNLOADED. `reason` bắt buộc "
                    + "(≥ 10 ký tự) và ghi `audit_log`.")
    @GetMapping("/batches/{batchId}/csv")
    public ResponseEntity<byte[]> downloadCsv(
            @CurrentUser SecurityPrincipal principal,
            @PathVariable String batchId,
            @RequestParam String reason,
            HttpServletRequest httpRequest) {
        AdminGuard.requireAnyRole(principal, WRITE_ROLES);
        String csv = adminService.downloadCsv(
                batchId, AdminContext.of(principal, reason, httpRequest));
        byte[] body = csv.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("activation-codes-" + sanitizeFilename(batchId) + ".csv")
                        .build().toString())
                // Mã thô tuyệt đối không được nằm trong cache của trình duyệt hay proxy.
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }

    // ------------------------------------------------------------------ L23

    @Operation(
            operationId = "voidAdminActivationCode",
            summary = "L23 — vô hiệu hoá một mã",
            description = "Chỉ áp dụng cho mã ISSUED. Mã REDEEMED ⇒ 409 ACTIVATION_CODE_ALREADY_USED "
                    + "(muốn thu hồi credit thì dùng điều chỉnh credit, không phải void mã).")
    @PostMapping("/{codeId}/void")
    public ActivationCodeAdminResponse voidCode(
            @CurrentUser SecurityPrincipal principal,
            @PathVariable UUID codeId,
            @Valid @RequestBody AdminReasonRequest request,
            HttpServletRequest httpRequest) {
        AdminGuard.requireAnyRole(principal, WRITE_ROLES);
        return ActivationCodeAdminResponse.from(adminService.voidCode(
                codeId, AdminContext.of(principal, request.reason(), httpRequest)));
    }

    // ------------------------------------------------------------------ L24

    @Operation(
            operationId = "voidAdminActivationBatch",
            summary = "L24 — vô hiệu hoá cả lô",
            description = "Chỉ đụng các mã còn ISSUED; số mã đã REDEEMED giữ nguyên.")
    @PostMapping("/batches/{batchId}/void")
    public ActivationBatchResponse voidBatch(
            @CurrentUser SecurityPrincipal principal,
            @PathVariable String batchId,
            @Valid @RequestBody AdminReasonRequest request,
            HttpServletRequest httpRequest) {
        AdminGuard.requireAnyRole(principal, WRITE_ROLES);
        adminService.voidBatch(batchId, AdminContext.of(principal, request.reason(), httpRequest));
        return adminService.findBatch(batchId)
                .map(batch -> ActivationBatchResponse.from(batch, adminService.csvAvailable(batchId)))
                .orElseThrow();
    }

    /* ---------------------------------------------------------------- helper */

    private static String csvPath(String batchId) {
        return "/api/v1/admin/activation-codes/batches/" + batchId + "/csv";
    }

    /** Tên tệp tải về — bỏ mọi ký tự có thể bị hiểu là đường dẫn hoặc header injection. */
    private static String sanitizeFilename(String batchId) {
        return batchId.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static ActivationCodeStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        // Giá trị lạ ⇒ IllegalArgumentException ⇒ 400 VALIDATION_FAILED (GlobalExceptionHandler),
        // không phải 500 và cũng không im lặng bỏ lọc.
        return ActivationCodeStatus.valueOf(status.strip().toUpperCase(java.util.Locale.ROOT));
    }

    private static int normalizePage(int page) {
        return Math.max(page, 0);
    }

    private static int normalizeSize(int size) {
        return Math.clamp(size, 1, MAX_PAGE_SIZE);
    }
}
