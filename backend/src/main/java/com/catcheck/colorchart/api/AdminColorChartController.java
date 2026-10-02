package com.catcheck.colorchart.api;

import com.catcheck.colorchart.api.dto.ColorChartDetailResponse;
import com.catcheck.colorchart.api.dto.ColorChartSummaryResponse;
import com.catcheck.colorchart.api.dto.CreateColorChartRequest;
import com.catcheck.colorchart.api.dto.OffsetPageResponse;
import com.catcheck.colorchart.api.dto.PhBandResponse;
import com.catcheck.colorchart.api.dto.PointInput;
import com.catcheck.colorchart.api.dto.ReplacePointsRequest;
import com.catcheck.colorchart.api.dto.UpdateColorChartRequest;
import com.catcheck.colorchart.api.dto.UpdatePhBandRequest;
import com.catcheck.colorchart.application.ColorChartAdminService;
import com.catcheck.colorchart.application.PhBandAdminService;
import com.catcheck.colorchart.domain.ChartStatus;
import com.catcheck.colorchart.domain.ColorChart;
import com.catcheck.colorchart.domain.PageResult;
import com.catcheck.colorchart.domain.PhClassificationBand;
import com.catcheck.shared.security.CurrentUser;
import com.catcheck.shared.security.SecurityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Quản trị bảng màu pH — L27 đến L32, L36, L37 (p8 §8.4.12).
 *
 * <p>Kiểm tra vai trò nằm trong controller (tầng api) — đây là quyết định "ai được gọi API này"
 * (p8 §8.3). Tên vai trò cụ thể do {@link ColorChartRoleGuard} giữ.
 *
 * <p>Chưa gắn {@code Aud} (ghi {@code audit_log}) và {@code S1:TOTP} (step-up) — thuộc W3 nối
 * SecurityConfig. Xem docs/handovers/A5.md.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Quản trị bảng màu pH", description = "L27–L32, L36–L37 — CRUD bảng màu và dải phân loại")
public class AdminColorChartController {

    private final ColorChartAdminService adminService;
    private final PhBandAdminService phBandAdminService;
    private final ColorChartRoleGuard roleGuard;

    public AdminColorChartController(ColorChartAdminService adminService,
                                     PhBandAdminService phBandAdminService,
                                     ColorChartRoleGuard roleGuard) {
        this.adminService = adminService;
        this.phBandAdminService = phBandAdminService;
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
}
