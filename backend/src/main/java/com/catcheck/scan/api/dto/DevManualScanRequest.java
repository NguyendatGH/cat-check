package com.catcheck.scan.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Body của {@code POST /api/v1/dev/scans} — đường nhập scan thủ công DEV-ONLY (mặc định TẮT,
 * không bao giờ tồn tại ở profile {@code prod}).
 *
 * <p>Không phải một phần hợp đồng API của p8: {@code POST /api/v1/scans} (multipart) vẫn là
 * đường duy nhất của sản phẩm. Record này tồn tại để dựng dữ liệu demo/dev có thật trong DB.
 *
 * @param catId          UUID hồ sơ mèo; bắt buộc khi {@code assignment = ASSIGNED}
 * @param assignment     {@code ASSIGNED} (mặc định khi có {@code catId}) hoặc {@code SHARED_UNKNOWN}
 * @param capturedAt     thời điểm quét, ISO-8601; cho lùi quá khứ để dựng lịch sử
 * @param phValue        pH nhập tay; để trống ⇒ {@code INCONCLUSIVE}
 * @param classification ép mã phân loại (p6 §6.7.1); để trống ⇒ suy ra từ {@code phValue}
 * @param confidence     để trống ⇒ mặc định của {@code ManualPipelineOutcomeFactory}
 * @param qualityFlags   danh sách mã {@code QualityFlagCode}; cờ {@code BLOCKING} ⇒ INCONCLUSIVE
 * @param captureSource  {@code CAMERA} (mặc định) hoặc {@code GALLERY}
 * @param deviceHint     chuỗi mô tả thiết bị, tuỳ chọn
 */
public record DevManualScanRequest(
        String catId,
        String assignment,
        @NotNull(message = "capturedAt là bắt buộc") Instant capturedAt,
        @DecimalMin(value = "3.0", message = "pH phải >= 3.0")
        @DecimalMax(value = "11.0", message = "pH phải <= 11.0")
        BigDecimal phValue,
        String classification,
        @DecimalMin(value = "0.0") @DecimalMax(value = "1.0") BigDecimal confidence,
        List<String> qualityFlags,
        String captureSource,
        @Size(max = 120) String deviceHint
) {
}
