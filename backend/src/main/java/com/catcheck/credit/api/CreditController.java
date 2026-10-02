package com.catcheck.credit.api;

import com.catcheck.credit.api.dto.ActivateCreditCodeRequest;
import com.catcheck.credit.api.dto.ActivationResponse;
import com.catcheck.credit.api.dto.BalanceResponse;
import com.catcheck.credit.api.dto.EntitlementResponse;
import com.catcheck.credit.api.dto.LedgerPageResponse;
import com.catcheck.credit.api.dto.PackagePlanListResponse;
import com.catcheck.credit.api.dto.PackagePlanResponse;
import com.catcheck.credit.application.ActivateCreditCodeService;
import com.catcheck.credit.application.CreditBalanceService;
import com.catcheck.credit.application.CreditLedgerHistoryService;
import com.catcheck.credit.application.EntitlementService;
import com.catcheck.credit.application.PackageCatalogService;
import com.catcheck.credit.application.FefoCreditConsumptionService;
import com.catcheck.credit.domain.Entitlement;
import com.catcheck.credit.domain.port.CreditLedgerQueryPort.LedgerPage;
import com.catcheck.shared.security.CurrentUser;
import com.catcheck.shared.security.SecurityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Bốn endpoint nghiệp vụ của module credit (p8 §8.4.3 — bảng H1..H4).
 *
 * <p>Tên path và {@code operationId} chép từ p8, không tự đặt lại: client sinh từ OpenAPI và
 * test hợp đồng của các module khác đều neo vào các tên này.</p>
 *
 * <p>Không có endpoint trừ credit ở đây — việc trừ nằm trong transaction của module scan
 * ({@link CreditConsumption}), không phải một HTTP call riêng. Xem javadoc của
 * {@link CreditConsumption}.</p>
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Credit", description = "Số dư, lịch sử giao dịch, kích hoạt gói và quyền tính năng")
public class CreditController {

    private final ActivateCreditCodeService activateCreditCodeService;
    private final CreditBalanceService creditBalanceService;
    private final CreditLedgerHistoryService creditLedgerHistoryService;
    private final EntitlementService entitlementService;
    private final PackageCatalogService packageCatalogService;

    public CreditController(
            ActivateCreditCodeService activateCreditCodeService,
            CreditBalanceService creditBalanceService,
            CreditLedgerHistoryService creditLedgerHistoryService,
            EntitlementService entitlementService,
            PackageCatalogService packageCatalogService
    ) {
        this.activateCreditCodeService = activateCreditCodeService;
        this.creditBalanceService = creditBalanceService;
        this.creditLedgerHistoryService = creditLedgerHistoryService;
        this.entitlementService = entitlementService;
        this.packageCatalogService = packageCatalogService;
    }

    /**
     * F3 — danh mục gói đang bán. CÔNG KHAI (p8 §8.4.6 cột Auth = {@code —}): trang giới thiệu
     * và màn cửa hàng hiển thị bảng giá trước khi người dùng đăng nhập.
     */
    @Operation(
            operationId = "listPackages",
            summary = "F3 — Danh mục gói đang bán",
            description = "Công khai. Chỉ gói `active`, sắp theo khối lượng tăng dần. "
                    + "Cờ tính năng trả phẳng giống `GET /entitlements/me`.")
    @GetMapping("/reference/packages")
    public ResponseEntity<PackagePlanListResponse> listPackages() {
        List<PackagePlanResponse> items = packageCatalogService.listActivePlans().stream()
                .map(PackagePlanResponse::from)
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                .body(new PackagePlanListResponse(items));
    }

    /** H1 — kích hoạt gói bằng mã in trên bao bì. */
    @Operation(
            operationId = "activateCreditCode",
            summary = "Kích hoạt gói bằng mã kích hoạt",
            description = """
                    Đổi một mã `ISSUED` lấy một lô credit. Mọi thao tác — khoá mã, tạo lô, ghi \
                    sổ cái, cập nhật quyền — nằm trong MỘT transaction. Sai định dạng/checksum bị \
                    bắt trước khi tra DB (400). Mã không tồn tại hoặc đã VOID → 422. Đã REDEEMED \
                    → 409. Quá hạn kích hoạt → 410.""")
    @PostMapping("/activations")
    public ResponseEntity<ActivationResponse> activate(
            @CurrentUser SecurityPrincipal user,
            @Valid @RequestBody ActivateCreditCodeRequest request,
            HttpServletRequest httpRequest) {

        ActivateCreditCodeService.ActivationResult result = activateCreditCodeService.activate(
                user.userId(), request.code(), ActivateCreditCodeService.RequestFacts.of(
                        httpRequest.getHeader("X-Request-Id"),
                        httpRequest.getRemoteAddr(),
                        httpRequest.getHeader("User-Agent")));

        // 200 chứ không phải 201: p8 §8.1.12 liệt kê "activations trả số dư" vào nhóm POST
        // không tạo tài nguyên mới (không có header Location).
        return ResponseEntity.ok(new ActivationResponse(
                result.packageCode(),
                result.packageName(),
                result.creditsGranted(),
                result.expiresAt(),
                result.balanceAfter()));
    }

    /**
     * H2 — số dư credit.
     *
     * <p>Không có tham số {@code userId}: endpoint đọc ID từ phiên đăng nhập. Cho phép client tự
     * truyền {@code userId} là lỗi IDOR kinh điển (bất biến I14).</p>
     */
    @Operation(
            operationId = "getCreditBalance",
            summary = "Số dư credit và số lượt trial còn lại",
            description = """
                    Tổng credit khả dụng + chi tiết từng lô kèm thời gian còn lại, sắp theo hạn \
                    tăng dần — đúng thứ tự FEFO sẽ tiêu credit.""")
    @GetMapping("/credits/balance")
    public BalanceResponse balance(@CurrentUser SecurityPrincipal user) {
        return BalanceResponse.from(creditBalanceService.balanceOf(user.userId()));
    }

    /**
     * H3 — lịch sử giao dịch.
     *
     * <p>Phân trang bằng con trỏ mờ: client lấy {@code nextCursor} của trang trước rồi truyền lại,
     * không tự dựng — con trỏ dựng sai chỉ bị bỏ qua chứ không gây 500.</p>
     */
    @Operation(
            operationId = "getCreditLedger",
            summary = "Lịch sử giao dịch credit, mới nhất trước",
            description = """
                    Phân trang keyset theo `(created_at, id)`. `cursor` rỗng = trang đầu. \
                    `limit` mặc định 20, tối đa 100.""")
    @GetMapping("/credits/ledger")
    public LedgerPageResponse ledger(
            @CurrentUser SecurityPrincipal user,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {

        LedgerPage page = creditLedgerHistoryService.page(
                user.userId(), LedgerCursorCodec.parse(cursor), limit);
        return LedgerPageResponse.from(page, LedgerCursorCodec.encode(page.nextCursor()));
    }

    /**
     * H4 — quyền hiện tại.
     *
     * <p>Không cache ở client: sau khi kích hoạt gói, quyền phải có hiệu lực ngay ở lần gọi kế
     * tiếp (p8 H4, p11 §11.5.5).</p>
     */
    @Operation(
            operationId = "getMyEntitlements",
            summary = "Quyền tính năng hiện tại của tôi",
            description = """
                    Quyền ĐỌC (lịch sử, trend, export) giữ vĩnh viễn theo gói cao nhất từng \
                    kích hoạt; quyền TẠO MỚI hết hạn cùng credit.""")
    @GetMapping("/entitlements/me")
    public EntitlementResponse entitlements(@CurrentUser SecurityPrincipal user) {
        Entitlement entitlement = entitlementService.snapshot(user.userId());
        return EntitlementResponse.from(
                entitlement, FefoCreditConsumptionService.TRIAL_SCAN_LIMIT);
    }
}
