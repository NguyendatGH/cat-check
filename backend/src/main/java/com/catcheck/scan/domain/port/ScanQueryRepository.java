package com.catcheck.scan.domain.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cổng truy vấn hiển thị/thống kê — luôn JOIN {@code scan} với {@code scan_analysis} hiện hành,
 * nên tách khỏi {@link ScanRepository} (thao tác aggregate theo id) để tránh N+1 khi liệt kê.
 */
public interface ScanQueryRepository {

    Page findHistory(HistoryFilter filter, String cursor, int limit);

    Summary summarize(HistoryFilter filter);

    /**
     * {@code n} scan hợp lệ gần nhất của một mèo, mới nhất trước — nguồn dữ liệu cho rule R1-R3
     * của insight (qua {@code scan.api.ScanHistoryQuery}, p6 §6.9).
     */
    List<RuleRow> findRecentForRules(UUID catId, Instant since, int limit);

    java.util.Optional<Row> findRowByScanId(UUID scanId);

    /**
     * Mọi lần quét của MỘT người dùng cho màn quản trị (p8 L5), phân trang <b>offset</b> kèm
     * tổng số dòng — cột {@code Trang = O} của bảng p8 §8.4.12 mục (a).
     *
     * <p>Khác {@link #findHistory} ở đúng hai điểm, cả hai cố ý:</p>
     * <ol>
     *   <li><b>Không lọc {@code INCONCLUSIVE}.</b> Màn lịch sử của người dùng bỏ các dòng không
     *       đọc được, còn tổng đài tra cứu chính vì "lần quét đó ra kết quả gì" — ẩn đi là ẩn
     *       đúng dòng người ta đang hỏi.</li>
     *   <li><b>Offset thay vì cursor</b> để màn admin nhảy trang và biết tổng số dòng
     *       (p8 §8.1.4 giải thích đánh đổi).</li>
     * </ol>
     *
     * <p>Vẫn giữ {@code deleted_at IS NULL}: dòng đã xoá mềm không thuộc phạm vi hỗ trợ của L5 —
     * truy cập dữ liệu đã xoá là việc của DSAR, không phải của màn tra cứu.</p>
     */
    OffsetPage findByUserForAdmin(UUID userId, int offset, int limit);

    /** Toàn bộ scan (kể cả disputed) của một mèo trong {@code [from, to]}, mới nhất trước — cho {@code export}. */
    List<Row> findForExport(UUID catId, Instant from, Instant to);

    record HistoryFilter(
            UUID userId,
            UUID catId,
            String assignment,
            List<String> classifications,
            Instant from,
            Instant to,
            Boolean disputed
    ) {
    }

    record Page(List<Row> items, String nextCursor) {
    }

    /**
     * Một trang offset: các dòng của trang + tổng số dòng khớp bộ lọc.
     *
     * <p>Tách khỏi {@link Page} (cursor) thay vì nhồi cả {@code nextCursor} lẫn
     * {@code totalElements} vào một record: một record mà nửa số trường luôn {@code null} là chỗ
     * để lẫn hai chế độ phân trang mà p8 §8.1.4 cố ý giữ riêng.</p>
     */
    record OffsetPage(List<Row> items, long totalElements) {

        public OffsetPage {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    /** Một dòng lịch sử — đủ trường cho {@code GET /scans} và {@code GET /scans/{id}} (p8 §8.5.4). */
    record Row(
            UUID scanId,
            UUID catId,
            UUID userId,
            String assignment,
            Instant capturedAt,
            String status,
            BigDecimal phValue,
            BigDecimal phLow,
            BigDecimal phHigh,
            String classification,
            UUID bandId,
            BigDecimal confidence,
            boolean nearBoundary,
            BigDecimal labL,
            BigDecimal labA,
            BigDecimal labB,
            Integer matchPercent,
            String calibrationMethod,
            UUID chartId,
            Integer chartVersion,
            boolean chartIsPlaceholder,
            String engineVersion,
            List<Map<String, Object>> qualityFlags,
            boolean creditCharged,
            boolean trial,
            boolean imageStored,
            Instant imageExpiresAt,
            String storeImageReason,
            Instant disputedAt,
            String disputedNote,
            short reassignCount,
            Integer processingMs,
            String scanRequestId
    ) {
    }

    /** Dòng rút gọn cho rule engine — chỉ các trường R1-R4 cần (p6 §6.9). */
    record RuleRow(
            UUID scanId,
            Instant capturedAt,
            String classification,
            BigDecimal phValue,
            BigDecimal confidence,
            boolean nearBoundary,
            String calibrationMethod
    ) {
    }

    record Summary(
            long count,
            Map<String, Long> byClassification,
            BigDecimal median,
            BigDecimal min,
            BigDecimal max,
            long inconclusiveCount,
            long lowConfidenceCount,
            Instant firstAt,
            Instant lastAt
    ) {
    }
}
