package com.catcheck.credit.api;

import com.catcheck.credit.api.dto.ActivateCreditCodeRequest;
import com.catcheck.credit.api.dto.ActivationResponse;
import com.catcheck.credit.api.dto.BalanceResponse;
import com.catcheck.credit.api.dto.ConsumeCreditRequest;
import com.catcheck.credit.api.dto.ConsumeCreditResponse;
import com.catcheck.credit.api.dto.EntitlementResponse;
import com.catcheck.credit.api.dto.LedgerPageResponse;
import com.catcheck.credit.api.dto.PackagePlanListResponse;
import com.catcheck.credit.api.dto.PackagePlanResponse;
import com.catcheck.credit.application.ActivateCreditCodeService;
import com.catcheck.credit.application.ConsumeCreditService;
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

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Credit", description = "Số dư, lịch sử giao dịch, kích hoạt gói và quyền tính năng")
public class CreditController {

    private final ActivateCreditCodeService activateCreditCodeService;
    private final ConsumeCreditService consumeCreditService;
    private final CreditBalanceService creditBalanceService;
    private final CreditLedgerHistoryService creditLedgerHistoryService;
    private final EntitlementService entitlementService;
    private final PackageCatalogService packageCatalogService;

    public CreditController(
            ActivateCreditCodeService activateCreditCodeService,
            ConsumeCreditService consumeCreditService,
            CreditBalanceService creditBalanceService,
            CreditLedgerHistoryService creditLedgerHistoryService,
            EntitlementService entitlementService,
            PackageCatalogService packageCatalogService
    ) {
        this.activateCreditCodeService = activateCreditCodeService;
        this.consumeCreditService = consumeCreditService;
        this.creditBalanceService = creditBalanceService;
        this.creditLedgerHistoryService = creditLedgerHistoryService;
        this.entitlementService = entitlementService;
        this.packageCatalogService = packageCatalogService;
    }

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

        return ResponseEntity.ok(new ActivationResponse(
                result.packageCode(),
                result.packageName(),
                result.creditsGranted(),
                result.expiresAt(),
                result.balanceAfter()));
    }

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

    @Operation(
            operationId = "consumeCredit",
            summary = "Trừ credit khi sử dụng dịch vụ (AI Chat, v.v.)",
            description = """
                    Trừ credit của người dùng theo nguyên tắc FEFO. Yêu cầu có đủ credit khả dụng.""")
    @PostMapping("/credits/consume")
    public ResponseEntity<ConsumeCreditResponse> consume(
            @CurrentUser SecurityPrincipal user,
            @Valid @RequestBody(required = false) ConsumeCreditRequest request) {

        int credits = (request != null && request.credits() != null) ? request.credits() : 1;
        String idempotencyKey = (request != null) ? request.idempotencyKey() : null;
        String note = (request != null) ? request.note() : null;

        com.catcheck.credit.api.CreditConsumption.CreditCharge charge = consumeCreditService.consume(
                user.userId(), credits, idempotencyKey, note);

        return ResponseEntity.ok(new ConsumeCreditResponse(charge.chargedCredits(), charge.balanceAfter()));
    }

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
