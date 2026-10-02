package com.catcheck.export.api;

import com.catcheck.export.api.dto.ExportJobListResponse;
import com.catcheck.export.api.dto.ExportJobResponse;
import com.catcheck.export.api.dto.ExportRequestRequest;
import com.catcheck.export.application.ExportQueryService;
import com.catcheck.export.application.ExportRequestCommand;
import com.catcheck.export.application.ExportRequestService;
import com.catcheck.shared.security.CurrentUser;
import com.catcheck.shared.security.SecurityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** Nhóm J — Xuất hồ sơ PDF (p8 §8.4.10, 4 endpoint). */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Export", description = "Xuất hồ sơ PDF theo dõi sức khoẻ cho bác sĩ thú y")
public class ExportController {

    private final ExportRequestService requestService;
    private final ExportQueryService queryService;

    public ExportController(ExportRequestService requestService, ExportQueryService queryService) {
        this.requestService = requestService;
        this.queryService = queryService;
    }

    /** J1 — {@code POST /exports}. */
    @Operation(operationId = "requestExport", summary = "Yêu cầu xuất hồ sơ PDF",
            description = "Tạo job xử lý nền, trả `202` + `Location`. Poll `GET /exports/{jobId}` để biết khi nào xong.")
    @PostMapping("/exports")
    public ResponseEntity<ExportJobResponse> requestExport(
            @CurrentUser SecurityPrincipal user, @Valid @RequestBody ExportRequestRequest request) {
        var job = requestService.request(new ExportRequestCommand(
                user.userId(), UUID.fromString(request.catId()), request.rangeFrom(), request.rangeTo(),
                request.rangePreset(), request.sections(), request.locale(), request.timezone()));
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/exports/" + job.getId()))
                .body(ExportJobResponse.from(job));
    }

    /** J2 — {@code GET /exports}. */
    @Operation(operationId = "listExports", summary = "Danh sách bản xuất của tôi")
    @GetMapping("/exports")
    public ExportJobListResponse listExports(
            @CurrentUser SecurityPrincipal user,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        var page = queryService.list(user.userId(), cursor, limit == null ? 20 : limit);
        List<ExportJobResponse> items = page.items().stream().map(ExportJobResponse::from).toList();
        return new ExportJobListResponse(items, page.nextCursor() != null, page.nextCursor());
    }

    /** J3 — {@code GET /exports/{jobId}}. */
    @Operation(operationId = "getExportJob", summary = "Trạng thái một job xuất PDF")
    @GetMapping("/exports/{jobId}")
    public ExportJobResponse getExportJob(@CurrentUser SecurityPrincipal user, @PathVariable UUID jobId) {
        return ExportJobResponse.from(queryService.detail(user.userId(), jobId));
    }

    /** J4 — {@code GET /exports/{jobId}/download}. */
    @Operation(operationId = "downloadExportJob", summary = "Tải file PDF đã xuất")
    @GetMapping("/exports/{jobId}/download")
    public ResponseEntity<InputStreamResource> downloadExportJob(
            @CurrentUser SecurityPrincipal user, @PathVariable UUID jobId) {
        var download = queryService.download(user.userId(), jobId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(download.bytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.filename() + "\"")
                .body(new InputStreamResource(download.content()));
    }
}
