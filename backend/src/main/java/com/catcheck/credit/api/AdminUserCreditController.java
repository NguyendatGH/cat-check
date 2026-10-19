package com.catcheck.credit.api;

import com.catcheck.credit.api.dto.AdminCreditBatchResponse;
import com.catcheck.credit.api.dto.AdminCreditOverviewResponse;
import com.catcheck.credit.api.dto.AdminLedgerEntryResponse;
import com.catcheck.credit.api.dto.CreditAdjustmentRequest;
import com.catcheck.credit.api.dto.CreditAdjustmentResponse;
import com.catcheck.credit.application.AdminCreditService;
import com.catcheck.shared.security.AdminGuard;
import com.catcheck.shared.security.CurrentUser;
import com.catcheck.shared.security.SecurityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Quản trị credit", description = "L9, L10 — lô credit, sổ ledger, điều chỉnh thủ công")
public class AdminUserCreditController {

    static final Set<String> READ_ROLES = Set.of("ADMIN_SUPER", "ADMIN_SUPPORT", "DPO");

    static final Set<String> ADJUST_ROLES = Set.of("ADMIN_SUPER");

    private final AdminCreditService adminCreditService;

    public AdminUserCreditController(AdminCreditService adminCreditService) {
        this.adminCreditService = adminCreditService;
    }


    @Operation(
            operationId = "getAdminUserCredits",
            summary = "L9 — lô credit + sổ ledger của một người dùng",
            description = "Trả TẤT CẢ lô kể cả đã hết hạn/cạn (p5 R3: lô đóng là chứng từ đối "
                    + "soát). Danh sách lô không phân trang; sổ ledger phân trang offset. KHÔNG trả "
                    + "`refId` của dòng do admin tạo — p14 §14.4.4 bước 9 không lộ danh tính admin.")
    @GetMapping("/{userId}/credits")
    public ResponseEntity<AdminCreditOverviewResponse> credits(
            @CurrentUser SecurityPrincipal principal,
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        AdminGuard.requireAnyRole(principal, READ_ROLES);
        AdminCreditService.AdminCreditOverview overview = adminCreditService.overview(
                userId, page, size, AdminContext.withoutReason(principal, httpRequest));

        AdminCreditOverviewResponse body = AdminCreditOverviewResponse.of(
                overview.availableBalance(),
                overview.batches().stream().map(AdminCreditBatchResponse::from).toList(),
                overview.ledger().stream().map(AdminLedgerEntryResponse::from).toList(),
                overview.ledgerPage(),
                overview.ledgerSize(),
                overview.ledgerTotal());

        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(body);
    }


    @Operation(
            operationId = "adjustAdminUserCredits",
            summary = "L10 — điều chỉnh credit thủ công (chỉ ADMIN_SUPER)",
            description = "`GRANT` tạo lô mới (activation_code_id = NULL) + dòng ledger GRANT; "
                    + "`REVOKE` ghi các dòng ADJUST âm trừ theo FEFO. `reason` bắt buộc ≥ 10 ký tự "
                    + "(400 REASON_REQUIRED). Vượt trần app_setting ⇒ 422 "
                    + "CREDIT_ADJUST_LIMIT_EXCEEDED; thu hồi quá số dư ⇒ 409 "
                    + "CREDIT_ADJUST_EXCEEDS_BALANCE. `Idempotency-Key` đi vào "
                    + "`credit_ledger.idempotency_key` (UNIQUE) nên gọi lại cùng khoá KHÔNG cấp lần hai.")
    @PostMapping("/{userId}/credit-adjustments")
    public CreditAdjustmentResponse adjust(
            @CurrentUser SecurityPrincipal principal,
            @PathVariable UUID userId,
            @Valid @RequestBody CreditAdjustmentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest httpRequest) {
        AdminGuard.requireAnyRole(principal, ADJUST_ROLES);
        return CreditAdjustmentResponse.from(adminCreditService.adjust(
                userId,
                parseDirection(request.direction()),
                request.amount(),
                request.packageCode(),
                request.validityDays(),
                idempotencyKey,
                AdminContext.of(principal, request.reason(), httpRequest)));
    }


    private static AdminCreditService.AdjustmentDirection parseDirection(String direction) {
        return AdminCreditService.AdjustmentDirection.valueOf(
                direction.strip().toUpperCase(Locale.ROOT));
    }
}
