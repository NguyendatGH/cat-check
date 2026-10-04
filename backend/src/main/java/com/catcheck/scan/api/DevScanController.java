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

/**
 * <b>DEV-ONLY</b> — nhập một kết quả quét bằng tay (đã biết pH, không có ảnh).
 *
 * <p><b>Vì sao cần:</b> pipeline ảnh cần OpenCV + ảnh thật + bảng màu đã hiệu chuẩn (p20 X1/X2:
 * bảng màu thật và bộ ảnh ground truth là dữ liệu owner CHƯA cung cấp). Mọi tính năng hạ
 * nguồn — lịch sử, xu hướng, export PDF, nhắc nhở, health flag, trừ credit FEFO — vì vậy không
 * có dữ liệu thật để chạy. Endpoint này ghi đúng một {@code scan} + {@code scan_analysis} qua
 * {@code ScanPersistenceService}, tức là CÙNG transaction, CÙNG FEFO, CÙNG {@code ScanSavedEvent}
 * như đường thật — chỉ thiếu bước đo màu.
 *
 * <p><b>Hai lớp khoá, theo đúng khuôn của {@code MockOAuth2Controller} và cờ fail-fast
 * {@code catcheck.scan.require-calibrated-chart} (p6 §6.6.6 — "an toàn theo mặc định ở prod"):</b>
 * <ul>
 *   <li>{@code @Profile("!prod")} — bean không tồn tại ở prod, không có cách nào bật lại bằng
 *       cấu hình;</li>
 *   <li>{@code @ConditionalOnProperty} KHÔNG có {@code matchIfMissing} — mặc định TẮT kể cả ở
 *       {@code local}/{@code dev}; phải đặt {@code catcheck.dev.manual-scan-enabled=true}
 *       (biến môi trường {@code CATCHECK_DEV_MANUAL_SCAN_ENABLED=true}) mới có endpoint.</li>
 * </ul>
 *
 * <p>Vẫn yêu cầu phiên đăng nhập hợp lệ ({@code anyRequest().authenticated()} của
 * {@code SecurityConfig}) và vẫn lấy {@code userId} từ phiên, không bao giờ từ body — không có
 * lý do để một đường dev được phép ghi scan cho tài khoản người khác.
 *
 * <p>KHÔNG nằm trong hợp đồng API của p8: {@code POST /api/v1/scans} (multipart) vẫn là đường
 * duy nhất của sản phẩm và không bị lớp này đụng tới.
 */
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

    /**
     * Ghi một kết quả quét nhập tay. Trả {@code 200} kèm đúng body của
     * {@code POST /api/v1/scans} để script seed và FE dùng chung một hình dạng dữ liệu.
     */
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
