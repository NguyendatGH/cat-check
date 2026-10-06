package com.catcheck.colorchart.api;

import com.catcheck.colorchart.api.dto.BackfillAcceptedResponse;
import com.catcheck.colorchart.api.dto.BackfillPreviewResponse;
import com.catcheck.colorchart.api.dto.ColorChartDetailResponse;
import com.catcheck.colorchart.api.dto.ColorChartSummaryResponse;
import com.catcheck.colorchart.api.dto.CreateColorChartRequest;
import com.catcheck.colorchart.api.dto.OffsetPageResponse;
import com.catcheck.colorchart.api.dto.PhBandResponse;
import com.catcheck.colorchart.api.dto.PointInput;
import com.catcheck.colorchart.api.dto.ReplacePointsRequest;
import com.catcheck.colorchart.api.dto.StartBackfillApplyRequest;
import com.catcheck.colorchart.api.dto.StartBackfillPreviewRequest;
import com.catcheck.colorchart.api.dto.UpdateColorChartRequest;
import com.catcheck.colorchart.api.dto.UpdatePhBandRequest;
import com.catcheck.colorchart.application.AdminAction;
import com.catcheck.colorchart.application.ChartBackfillAdminService;
import com.catcheck.colorchart.application.ColorChartAdminService;
import com.catcheck.colorchart.application.PhBandAdminService;
import com.catcheck.colorchart.domain.ChartStatus;
import com.catcheck.colorchart.domain.ColorChart;
import com.catcheck.colorchart.domain.PageResult;
import com.catcheck.colorchart.domain.PhClassificationBand;
import com.catcheck.shared.security.AdminGuard;
import com.catcheck.shared.security.CurrentUser;
import com.catcheck.shared.security.SecurityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Period;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Quản trị bảng màu pH — L27 đến L37 (p8 §8.4.12 mục (c)).
 *
 * <p>Kiểm tra vai trò nằm trong controller (tầng api) — đây là quyết định "ai được gọi API này"
 * (p8 §8.3). Tên vai trò cụ thể do {@link ColorChartRoleGuard} giữ.
 *
 * <p>Chưa gắn {@code Aud} (ghi {@code audit_log}) và {@code S1:TOTP} (step-up) — thuộc W3 nối
 * SecurityConfig. Xem docs/handovers/A5.md.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Quản trị bảng màu pH",
        description = "L27–L37 — CRUD bảng màu, backfill kết quả cũ, dải phân loại")
public class AdminColorChartController {

    /**
     * Vai trò ghi {@code audit_log.actor_role} — vai trò MẠNH NHẤT đã cho phép hành động đi qua
     * (p4 §4.6.3). Một tài khoản có thể giữ nhiều vai trò nhưng cột chỉ chứa một.
     */
    private static final List<String> ADMIN_ROLE_PRIORITY = List.of("ADMIN_SUPER", "ADMIN_CATALOG");

    private final ColorChartAdminService adminService;
    private final PhBandAdminService phBandAdminService;
    private final ChartBackfillAdminService backfillService;
    private final ColorChartRoleGuard roleGuard;

    public AdminColorChartController(ColorChartAdminService adminService,
                                     PhBandAdminService phBandAdminService,
                                     ChartBackfillAdminService backfillService,
                                     ColorChartRoleGuard roleGuard) {
        this.adminService = adminService;
        this.phBandAdminService = phBandAdminService;
        this.backfillService = backfillService;
        this.roleGuard = roleGuard;
    }

    // ------------------------------------------------------------------ L27

    @Operation(
            operationId = "listAdminColorCharts",
            summary = "Danh sách bảng màu mọi trạng thái",
            description = "L27. Phân trang kiểu offset vì admin cần nhảy trang và tổng số dòng.")
    @GetMapping("/color-charts")
    public OffsetPageResponse<ColorChartSummaryResponse> list(
            @CurrentUser SecurityPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        roleGuard.requireChartAdmin(principal);
        ChartStatus statusFilter = EnumParam.parse(ChartStatus.class, status, "status");
        PageResult<ColorChart> result = adminService.listForAdmin(statusFilter, page, size);
        return new OffsetPageResponse<>(
                result.items().stream().map(ColorChartSummaryResponse::from).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages(),
                result.hasMore());
    }

    // ------------------------------------------------------------------ L28

    @Operation(
            operationId = "createColorChartDraft",
            summary = "Tạo bảng màu DRAFT",
            description = "L28. Tạo bản DRAFT mới (hoặc clone từ version đang ACTIVE).")
    @PostMapping("/color-charts")
    public ResponseEntity<ColorChartDetailResponse> create(
            @CurrentUser SecurityPrincipal principal,
            @Valid @RequestBody CreateColorChartRequest request) {
        roleGuard.requireChartAdmin(principal);
        ColorChart chart = adminService.createDraft(
                request.code(), request.name(), request.productLine(),
                request.productionBatch(), null, principal.userId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ColorChartDetailResponse.from(adminService.findDetail(chart.getId())));
    }

    // ------------------------------------------------------------------ L29

    @Operation(
            operationId = "getColorChartDetail",
            summary = "Chi tiết bảng màu + toàn bộ điểm",
            description = "L29. Trả ETag để L30/L31 dùng If-Match (p8 §8.1.11).")
    @GetMapping("/color-charts/{chartId}")
    public ResponseEntity<ColorChartDetailResponse> detail(
            @CurrentUser SecurityPrincipal principal,
            @PathVariable UUID chartId) {
        roleGuard.requireChartAdmin(principal);
        ColorChartDetailResponse detail = ColorChartDetailResponse.from(adminService.findDetail(chartId));
        return ResponseEntity.ok()
                .eTag(ETag.of(adminService.findDetail(chartId).chart()))
                .body(detail);
    }

    // ------------------------------------------------------------------ L30

    @Operation(
            operationId = "updateColorChartDraft",
            summary = "Sửa metadata bản DRAFT",
            description = "L30. Chỉ khi DRAFT; sửa bản ACTIVE ⇒ 409 COLOR_CHART_IN_USE. If-Match bắt buộc.")
    @PatchMapping("/color-charts/{chartId}")
    public ResponseEntity<ColorChartDetailResponse> update(
            @CurrentUser SecurityPrincipal principal,
            @PathVariable UUID chartId,
            @RequestHeader(name = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody UpdateColorChartRequest request) {
        roleGuard.requireChartAdmin(principal);
        ColorChart chart = adminService.findDetail(chartId).chart();
        ETag.requireMatch(ifMatch, chart);
        ColorChart updated = adminService.updateDraft(
                chartId, request.name(), request.productLine(), request.productionBatch(), null);
        return ResponseEntity.ok()
                .eTag(ETag.of(updated))
                .body(ColorChartDetailResponse.from(adminService.findDetail(chartId)));
    }

    // ------------------------------------------------------------------ L31

    @Operation(
            operationId = "replaceColorChartPoints",
            summary = "Thay toàn bộ mức pH",
            description = "L31. Nhập hex hoặc Lab. If-Match bắt buộc.")
    @PutMapping("/color-charts/{chartId}/points")
    public ResponseEntity<ColorChartDetailResponse> replacePoints(
            @CurrentUser SecurityPrincipal principal,
            @PathVariable UUID chartId,
            @RequestHeader(name = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody ReplacePointsRequest request) {
        roleGuard.requireChartAdmin(principal);
        ColorChart chart = adminService.findDetail(chartId).chart();
        ETag.requireMatch(ifMatch, chart);
        List<ColorChartAdminService.PointInput> inputs = request.points().stream()
                .map(p -> new ColorChartAdminService.PointInput(
                        p.phValue(), p.labL(), p.labA(), p.labB(), p.toleranceDeltaE(),
                        p.hexSrgb(), p.displayHex(), p.displayNameVi(), p.displayNameEn()))
                .toList();
        return ResponseEntity.ok()
                .eTag(ETag.of(adminService.findDetail(chartId).chart()))
                .body(ColorChartDetailResponse.from(adminService.replacePoints(chartId, inputs)));
    }

    // ------------------------------------------------------------------ L32

    @Operation(
            operationId = "publishColorChart",
            summary = "Publish bảng màu",
            description = "L32. DRAFT ⇒ ACTIVE, bản cũ ⇒ ARCHIVED. Thiếu điểm/dải ⇒ 422 COLOR_CHART_INCOMPLETE.")
    @PostMapping("/color-charts/{chartId}/publish")
    public ResponseEntity<ColorChartDetailResponse> publish(
            @CurrentUser SecurityPrincipal principal,
            @PathVariable UUID chartId) {
        roleGuard.requireChartAdmin(principal);
        ColorChart published = adminService.publish(chartId);
        return ResponseEntity.ok()
                .eTag(ETag.of(published))
                .body(ColorChartDetailResponse.from(adminService.findDetail(chartId)));
    }

    // ------------------------------------------------------------------ L33

    @Operation(
            operationId = "startColorChartBackfillPreview",
            summary = "Tính thử kết quả cũ theo bảng màu mới",
            description = """
                    L33. Chạy NỀN (`202`): ghi `scan_analysis_recompute`, KHÔNG đổi kết quả nào \
                    đang hiển thị. `reason` bắt buộc ≥ 10 ký tự. Bảng `ARCHIVED` ⇒ \
                    `409 COLOR_CHART_IN_USE`; bảng chưa đủ 2 mức pH ⇒ `422 COLOR_CHART_INCOMPLETE`. \
                    Header `Location` trỏ tới L34.""")
    @PostMapping("/color-charts/{chartId}/backfill-preview")
    public ResponseEntity<BackfillAcceptedResponse> startBackfillPreview(
            @CurrentUser SecurityPrincipal principal,
            @PathVariable UUID chartId,
            @Valid @RequestBody StartBackfillPreviewRequest request,
            HttpServletRequest httpRequest) {
        roleGuard.requireChartAdmin(principal);
        Period window = ChartBackfillAdminService.parseWindow(request.window());
        backfillService.startPreview(chartId, request.window(), adminAction(principal, request.reason(), httpRequest));
        String statusUrl = previewUrl(chartId);
        return ResponseEntity.accepted()
                .location(URI.create(statusUrl))
                .body(new BackfillAcceptedResponse(chartId.toString(), window.toString(), statusUrl));
    }

    // ------------------------------------------------------------------ L34

    @Operation(
            operationId = "getColorChartBackfillPreview",
            summary = "Tác động của lượt tính thử",
            description = """
                    L34. Số bản ghi bị LẬT phân loại và `deltaPh` lớn nhất, kèm `evaluated` làm \
                    mẫu số — "12 bản ghi bị lật" không nói được gì nếu không biết 12 trên bao nhiêu.""")
    @GetMapping("/color-charts/{chartId}/backfill-preview")
    public BackfillPreviewResponse getBackfillPreview(
            @CurrentUser SecurityPrincipal principal,
            @PathVariable UUID chartId) {
        roleGuard.requireChartAdmin(principal);
        return BackfillPreviewResponse.from(chartId.toString(), backfillService.impact(chartId));
    }

    // ------------------------------------------------------------------ L35

    @Operation(
            operationId = "applyColorChartBackfill",
            summary = "Áp dụng kết quả tính thử",
            description = """
                    L35. Chạy NỀN (`202`): tạo dòng `scan_analysis` mới (`recompute_of` trỏ về bản \
                    gốc) và chuyển `is_current`. Chưa chạy preview ⇒ `422 COLOR_CHART_INCOMPLETE`.""")
    @PostMapping("/color-charts/{chartId}/backfill-apply")
    public ResponseEntity<BackfillAcceptedResponse> applyBackfill(
            @CurrentUser SecurityPrincipal principal,
            @PathVariable UUID chartId,
            @Valid @RequestBody StartBackfillApplyRequest request,
            HttpServletRequest httpRequest) {
        roleGuard.requireChartAdmin(principal);
        backfillService.startApply(chartId, adminAction(principal, request.reason(), httpRequest));
        String statusUrl = previewUrl(chartId);
        return ResponseEntity.accepted()
                .location(URI.create(statusUrl))
                .body(new BackfillAcceptedResponse(chartId.toString(), null, statusUrl));
    }

    // ------------------------------------------------------------------ L36

    @Operation(
            operationId = "listAdminPhBands",
            summary = "Danh sách dải phân loại",
            description = "L36. 6 dải toàn cục (đủ cả hai locale) + ETag.")
    @GetMapping("/ph-classification-bands")
    public ResponseEntity<List<PhBandResponse>> listBands(
            @CurrentUser SecurityPrincipal principal,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, defaultValue = "vi") String acceptLanguage) {
        roleGuard.requireChartAdmin(principal);
        Locale locale = Locale.forLanguageTag(acceptLanguage);
        List<PhBandResponse> bands = phBandAdminService.listBands().stream()
                .map(band -> PhBandResponse.from(band, locale.getLanguage()))
                .toList();
        return ResponseEntity.ok()
                .eTag(ETag.ofBands(phBandAdminService.listBands()))
                .body(bands);
    }

    // ------------------------------------------------------------------ L37

    @Operation(
            operationId = "updatePhBand",
            summary = "Sửa dải phân loại",
            description = "L37. Sửa nhãn/màu/icon. If-Match bắt buộc; đổi ngưỡng ⇒ step-up (W3).")
    @PatchMapping("/ph-classification-bands/{code}")
    public ResponseEntity<PhBandResponse> updateBand(
            @CurrentUser SecurityPrincipal principal,
            @PathVariable String code,
            @RequestHeader(name = "If-Match", required = false) String ifMatch,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, defaultValue = "vi") String acceptLanguage,
            @Valid @RequestBody UpdatePhBandRequest request) {
        roleGuard.requireChartAdmin(principal);
        PhClassificationBand band = phBandAdminService.listBands().stream()
                .filter(b -> b.getCode().equals(code))
                .findFirst()
                .orElseThrow();
        ETag.requireMatch(ifMatch, band);
        PhBandAdminService.BandUpdate update = new PhBandAdminService.BandUpdate(
                request.minPh(), request.maxPh(), request.minInclusive(), request.maxInclusive(),
                request.labelVi(), request.labelEn(), request.descriptionVi(), request.descriptionEn(),
                request.severity(), request.colorToken(), request.iconName(),
                request.triggersAlert(), request.sortOrder(), request.active());
        PhClassificationBand updated = phBandAdminService.updateBand(code, update);
        Locale locale = Locale.forLanguageTag(acceptLanguage);
        return ResponseEntity.ok()
                .eTag(ETag.of(updated))
                .body(PhBandResponse.from(updated, locale.getLanguage()));
    }

    /**
     * Gộp "kiểm {@code reason}" và "dựng ngữ cảnh audit" vào một lời gọi — tách ra thì một
     * endpoint mới có thể dựng ngữ cảnh mà quên kiểm {@code reason}, và lỗi đó chỉ lộ ra khi
     * có người đọc lại {@code audit_log} nhiều tháng sau.
     */
    private static AdminAction adminAction(SecurityPrincipal principal, String reason,
                                           HttpServletRequest request) {
        return new AdminAction(
                principal.userId(),
                actorRole(principal),
                AdminGuard.requireReason(reason),
                request.getHeader("X-Request-Id"),
                request.getRemoteAddr(),
                request.getHeader("User-Agent"));
    }

    private static String actorRole(SecurityPrincipal principal) {
        for (String candidate : ADMIN_ROLE_PRIORITY) {
            if (AdminGuard.hasAnyRole(principal, Set.of(candidate))) {
                return candidate;
            }
        }
        return principal.roles().stream().findFirst().orElse(null);
    }

    private static String previewUrl(UUID chartId) {
        return "/api/v1/admin/color-charts/" + chartId + "/backfill-preview";
    }
}
