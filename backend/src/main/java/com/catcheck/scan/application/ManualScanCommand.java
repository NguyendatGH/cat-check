package com.catcheck.scan.application;

import com.catcheck.scan.domain.CaptureSource;
import com.catcheck.scan.domain.QualityFlag;
import com.catcheck.scan.domain.ScanAssignment;
import com.catcheck.scan.domain.ScanClassification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lệnh của đường nhập scan THỦ CÔNG dev-only ({@code POST /api/v1/dev/scans}) — bản không-ảnh
 * của {@link SubmitScanCommand}. Tầng {@code api} dựng record này từ JSON trước khi gọi xuống
 * {@link ManualScanIngestService} (R2/R3: application không biết kiểu HTTP).
 *
 * @param userId               chủ sở hữu, LUÔN lấy từ phiên chứ không từ body (bất biến I14)
 * @param catId                {@code null} khi {@code assignment = SHARED_UNKNOWN}
 * @param capturedAt           cho phép lùi quá khứ — đó là lý do đường này tồn tại
 * @param forcedClassification ép mã phân loại; {@code null} ⇒ suy từ {@code phValue}
 * @param confidence           {@code null} ⇒ mặc định của {@link ManualPipelineOutcomeFactory}
 */
public record ManualScanCommand(
        UUID userId,
        UUID catId,
        ScanAssignment assignment,
        Instant capturedAt,
        CaptureSource captureSource,
        String deviceHint,
        String idempotencyKey,
        BigDecimal phValue,
        ScanClassification forcedClassification,
        BigDecimal confidence,
        List<QualityFlag> qualityFlags) {

    public ManualScanCommand {
        if (userId == null) {
            throw new IllegalArgumentException("manualScanCommand.userId là bắt buộc");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("manualScanCommand.idempotencyKey là bắt buộc");
        }
        captureSource = captureSource == null ? CaptureSource.CAMERA : captureSource;
        qualityFlags = qualityFlags == null ? List.of() : List.copyOf(qualityFlags);
    }
}
