package com.catcheck.scan.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * <b>HỢP ĐỒNG ĐỌC LỊCH SỬ SCAN — module {@code insight} dùng để đánh giá rule R1-R4 (p6 §6.9).</b>
 *
 * <p>Chỉ trả các scan HỢP LỆ để đưa vào rule (p6 §6.9.1 nguyên tắc 2-4, đã lọc sẵn ở phía
 * {@code scan}, insight không cần lọc lại): {@code deleted_at IS NULL},
 * {@code disputed_at IS NULL}, {@code assignment = 'ASSIGNED'}, {@code status = 'ANALYZED'}.
 * insight còn phải tự lọc thêm theo {@code minConfidence}/{@code calibration_method} của TỪNG
 * rule (khác nhau giữa R1/R2/R3) trên tập trả về.</p>
 */
public interface ScanHistoryQuery {

    /** {@code limit} scan hợp lệ gần nhất của một mèo kể từ {@code since}, mới nhất trước. */
    List<RecentScan> recentValidScans(UUID catId, Instant since, int limit);

    /**
     * Toàn bộ scan (kể cả disputed, KHÔNG kể đã xoá mềm) của một mèo trong khoảng
     * {@code [from, to]} — nguồn dữ liệu cho {@code export} (p13 Khối 3-5). Mới nhất trước.
     */
    List<ExportScanRow> scansForExport(UUID catId, Instant from, Instant to);

    record ExportScanRow(
            UUID scanId,
            Instant capturedAt,
            BigDecimal phValue,
            String classification,
            BigDecimal confidence,
            boolean nearBoundary,
            boolean disputed,
            String calibrationMethod,
            List<String> qualityFlagCodes,
            boolean imageAvailable
    ) {
    }

    record RecentScan(
            UUID scanId,
            Instant capturedAt,
            String classification,
            BigDecimal phValue,
            BigDecimal confidence,
            boolean nearBoundary,
            String calibrationMethod
    ) {
    }
}
