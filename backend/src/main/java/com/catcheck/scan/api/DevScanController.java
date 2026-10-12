package com.catcheck.scan.api;

import com.catcheck.scan.api.dto.DevManualScanRequest;
import com.catcheck.scan.api.dto.ScanResultResponse;
import com.catcheck.scan.application.ManualScanIngestService;
import com.catcheck.scan.application.ScanSubmitResult;
import com.catcheck.shared.security.CurrentUser;
import com.catcheck.shared.security.SecurityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dev")
@Profile("!prod")
@ConditionalOnProperty(name = "catcheck.dev.manual-scan-enabled", havingValue = "true")
@Tag(name = "Dev", description = "Công cụ dev-only, không có ở prod")
public class DevScanController {

    private final ManualScanIngestService ingestService;

    public DevScanController(ManualScanIngestService ingestService) {
        this.ingestService = ingestService;
    }

    @Operation(
            operationId = "devCreateManualScan",
            summary = "DEV-ONLY — ghi một kết quả quét từ pH nhập tay (không cần ảnh)",
            description = """
                    Chỉ tồn tại khi `catcheck.dev.manual-scan-enabled=true` và profile khác \
                    `prod`. Bắt buộc header `Idempotency-Key`: gọi lại cùng khoá trả nguyên \
                    kết quả cũ và KHÔNG trừ credit lần hai.""")
    @PostMapping("/scans")
    public ResponseEntity<ScanResultResponse> createManualScan(
            @CurrentUser SecurityPrincipal user,
            @Valid @RequestBody DevManualScanRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        ScanSubmitResult result = ingestService.ingest(
                DevScanRequestSupport.toCommand(user.userId(), request, idempotencyKey));
        return ResponseEntity.ok(ScanResultResponse.from(result, null));
    }
}
