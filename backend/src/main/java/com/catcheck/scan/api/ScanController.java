package com.catcheck.scan.api;

import com.catcheck.scan.api.dto.DisputeRequest;
import com.catcheck.scan.api.dto.DisputeResponse;
import com.catcheck.scan.api.dto.ReassignCatRequest;
import com.catcheck.scan.api.dto.ReassignCatResponse;
import com.catcheck.scan.api.dto.ScanAnalysisResponse;
import com.catcheck.scan.api.dto.ScanConfigResponse;
import com.catcheck.scan.api.dto.ScanListItemResponse;
import com.catcheck.scan.api.dto.ScanListResponse;
import com.catcheck.scan.api.dto.ScanMetadataRequest;
import com.catcheck.scan.api.dto.ScanResultResponse;
import com.catcheck.scan.api.dto.ScanSummaryResponse;
import com.catcheck.scan.application.ScanConfigService;
import com.catcheck.scan.application.ScanImageAccessService;
import com.catcheck.scan.application.ScanLifecycleService;
import com.catcheck.scan.application.ScanQueryService;
import com.catcheck.scan.application.SubmitScanCommand;
import com.catcheck.scan.application.SubmitScanService;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.security.CurrentUser;
import com.catcheck.shared.security.SecurityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Nhóm E — Quét &amp; phân tích (p8 §8.4.5, 12 endpoint) + E12 {@code GET /scan/config}.
 *
 * <p>Controller KHÔNG chứa nghiệp vụ (R4): mọi kiểu domain được ánh xạ sang record ở
 * {@code api.dto} trước khi ra khỏi lớp này.</p>
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Scan", description = "Quét ảnh cát, lịch sử, phân tích chi tiết")
public class ScanController {

    private final SubmitScanService submitScanService;
    private final ScanQueryService queryService;
    private final ScanLifecycleService lifecycleService;
    private final ScanImageAccessService imageAccessService;
    private final ScanConfigService configService;
    private final ThreadPoolTaskExecutor scanExecutor;

    public ScanController(
            SubmitScanService submitScanService,
            ScanQueryService queryService,
            ScanLifecycleService lifecycleService,
            ScanImageAccessService imageAccessService,
            ScanConfigService configService,
            ThreadPoolTaskExecutor scanExecutor) {
        this.submitScanService = submitScanService;
        this.queryService = queryService;
        this.lifecycleService = lifecycleService;
        this.imageAccessService = imageAccessService;
        this.configService = configService;
        this.scanExecutor = scanExecutor;
    }

    /** E1 — {@code POST /scans}, một bước (TD-02, p7 §7.4.5). */
    @Operation(
            operationId = "submitScan",
            summary = "Phân tích ảnh cát + lưu + trừ credit trong một bước",
            description = """
                    `multipart/form-data`: part `metadata` (JSON) + part `image` (binary). \
                    Bắt buộc header `Idempotency-Key` = `metadata.scanRequestId`. Trả `200` cả khi \
                    kết quả là `INCONCLUSIVE` (không phải lỗi) — xem `classification`.""")
    @PostMapping(value = "/scans", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CompletableFuture<ResponseEntity<ScanResultResponse>> submitScan(
            @CurrentUser SecurityPrincipal user,
            @RequestPart("metadata") @Valid ScanMetadataRequest metadata,
            @RequestPart("image") MultipartFile image,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        if (!idempotencyKey.equals(metadata.scanRequestId())) {
            throw new BusinessRuleException(ScanErrorCode.SCAN_METADATA_INVALID);
        }
        SubmitScanCommand command = ScanRequestSupport.toCommand(
                user.userId(), metadata, idempotencyKey, readBytes(image));

        try {
            return CompletableFuture
                    .supplyAsync(() -> submitScanService.submit(command), scanExecutor)
                    .thenApply(result -> ResponseEntity.ok(
                            ScanResultResponse.from(result, imageUrlOf(result.scanId()))));
        } catch (TaskRejectedException ex) {
            throw new BusinessRuleException(ScanErrorCode.SCAN_BUSY);
        }
    }

    /** E2 — {@code GET /scans}. */
    @Operation(operationId = "listScans", summary = "Lịch sử quét",
            description = "Phân trang con trỏ, sắp `capturedAt,desc` duy nhất (khoá cursor).")
    @GetMapping("/scans")
    public ScanListResponse listScans(
            @CurrentUser SecurityPrincipal user,
            @RequestParam(required = false) UUID catId,
            @RequestParam(required = false) String assignment,
            @RequestParam(required = false) List<String> classification,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) Boolean disputed,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {

        var page = queryService.history(user.userId(), catId, assignment, classification, from, to,
                disputed, cursor, limit == null ? 20 : limit);
        List<ScanListItemResponse> items = ScanRequestSupport.toListItems(page.items(), queryService::catName);
        return new ScanListResponse(items, page.nextCursor() != null, page.nextCursor());
    }

    /** E3 — {@code GET /scans/summary}. */
    @Operation(operationId = "getScanSummary", summary = "Thống kê quét trong một khoảng thời gian")
    @GetMapping("/scans/summary")
    public ScanSummaryResponse scanSummary(
            @CurrentUser SecurityPrincipal user,
            @RequestParam(required = false) UUID catId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return ScanSummaryResponse.from(queryService.summary(user.userId(), catId, from, to));
    }

    /** E4 — {@code GET /scans/{scanId}}. */
    @Operation(operationId = "getScan", summary = "Chi tiết một lần quét (kết quả hiển thị)")
    @GetMapping("/scans/{scanId}")
    public ScanResultResponse getScan(@CurrentUser SecurityPrincipal user, @PathVariable UUID scanId) {
        var row = queryService.detail(user.userId(), scanId);
        String catName = queryService.catName(row.catId());
        String imageUrl = row.imageStored() ? imageUrlOf(row.scanId()) : null;
        return ScanRequestSupport.toResultResponse(row, catName, imageUrl);
    }

    /** E5 — {@code GET /scans/{scanId}/analysis}. */
    @Operation(operationId = "getScanAnalysis", summary = "Phân tích chi tiết (Lab, ΔE, hiệu chỉnh, chất lượng)")
    @GetMapping("/scans/{scanId}/analysis")
    public ScanAnalysisResponse getScanAnalysis(@CurrentUser SecurityPrincipal user, @PathVariable UUID scanId) {
        return ScanAnalysisResponse.from(queryService.analysis(user.userId(), scanId));
    }

    /** E6 — {@code GET /scans/by-request/{scanRequestId}}. */
    @Operation(operationId = "getScanByRequest", summary = "Poll kết quả theo scanRequestId sau 409 SCAN_IN_PROGRESS")
    @GetMapping("/scans/by-request/{scanRequestId}")
    public ScanResultResponse getScanByRequest(@CurrentUser SecurityPrincipal user, @PathVariable String scanRequestId) {
        var row = queryService.byRequest(user.userId(), scanRequestId);
        String catName = queryService.catName(row.catId());
        String imageUrl = row.imageStored() ? imageUrlOf(row.scanId()) : null;
        return ScanRequestSupport.toResultResponse(row, catName, imageUrl);
    }

    /** E7 — {@code GET /scans/{scanId}/image}. */
    @Operation(operationId = "getScanImage", summary = "Ảnh gốc của một lần quét")
    @GetMapping("/scans/{scanId}/image")
    public ResponseEntity<InputStreamResource> getScanImage(
            @CurrentUser SecurityPrincipal user, @PathVariable UUID scanId) {
        var result = imageAccessService.open(user.userId(), scanId);
        if (result instanceof ScanImageAccessService.Redirect redirect) {
            return ResponseEntity.status(302).location(redirect.location()).build();
        }
        ScanImageAccessService.Stream stream = (ScanImageAccessService.Stream) result;
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(stream.contentType()))
                .contentLength(stream.bytes())
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .body(new InputStreamResource(stream.content()));
    }

    /** E8 — {@code DELETE /scans/{scanId}}. */
    @Operation(operationId = "deleteScan", summary = "Xoá mềm một lần quét + xoá cứng file ảnh")
    @DeleteMapping("/scans/{scanId}")
    public ResponseEntity<Void> deleteScan(@CurrentUser SecurityPrincipal user, @PathVariable UUID scanId) {
        lifecycleService.delete(user.userId(), scanId);
        return ResponseEntity.noContent().build();
    }

    /** E9 — {@code POST /scans/{scanId}/reassign-cat}. */
    @Operation(operationId = "reassignScanCat", summary = "Gán lại kết quả cho mèo khác (≤24h, ≤3 lần)")
    @PostMapping("/scans/{scanId}/reassign-cat")
    public ReassignCatResponse reassignScanCat(
            @CurrentUser SecurityPrincipal user, @PathVariable UUID scanId,
            @Valid @RequestBody ReassignCatRequest request) {
        boolean toShared = "SHARED_UNKNOWN".equals(request.toAssignment());
        UUID toCatId = toShared || request.toCatId() == null ? null : UUID.fromString(request.toCatId());
        var result = lifecycleService.reassign(user.userId(), scanId, toCatId, toShared, null);
        return new ReassignCatResponse(
                result.scanId().toString(),
                result.fromCatId() == null ? null : result.fromCatId().toString(),
                result.toCatId() == null ? null : result.toCatId().toString(),
                result.reassignRemaining(),
                List.of(),
                List.of());
    }

    /** E10 — {@code POST /scans/{scanId}/dispute}. */
    @Operation(operationId = "disputeScan", summary = "Đánh dấu kết quả này không chính xác")
    @PostMapping("/scans/{scanId}/dispute")
    public DisputeResponse disputeScan(
            @CurrentUser SecurityPrincipal user, @PathVariable UUID scanId,
            @Valid @RequestBody(required = false) DisputeRequest request) {
        Instant disputedAt = lifecycleService.dispute(user.userId(), scanId, request == null ? null : request.note());
        return new DisputeResponse(scanId.toString(), disputedAt, true);
    }

    /** E11 — {@code DELETE /scans/{scanId}/dispute}. */
    @Operation(operationId = "clearScanDispute", summary = "Gỡ đánh dấu tranh chấp")
    @DeleteMapping("/scans/{scanId}/dispute")
    public ResponseEntity<Void> clearScanDispute(@CurrentUser SecurityPrincipal user, @PathVariable UUID scanId) {
        lifecycleService.clearDispute(user.userId(), scanId);
        return ResponseEntity.noContent().build();
    }

    /** E12 — {@code GET /scan/config}. */
    @Operation(operationId = "getScanConfig", summary = "Tham số & ngưỡng chất lượng cho client")
    @GetMapping("/scan/config")
    public ScanConfigResponse getScanConfig() {
        return ScanConfigResponse.from(configService.snapshot());
    }

    private String imageUrlOf(UUID scanId) {
        return scanId == null ? null : "/api/v1/scans/" + scanId + "/image";
    }

    private byte[] readBytes(MultipartFile image) {
        try {
            return image.getBytes();
        } catch (IOException ex) {
            throw new BusinessRuleException(ScanErrorCode.IMAGE_DECODE_FAILED);
        }
    }
}
