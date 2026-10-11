package com.catcheck.scan.infrastructure.persistence;

import com.catcheck.scan.domain.ScanAnalysis;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ScanAnalysisJpaRepository extends JpaRepository<ScanAnalysis, UUID> {

    /**
     * Điều kiện ứng viên backfill (p6 §6.5.4), dùng chung cho câu lấy lô và câu đếm.
     *
     * <p>Ngoài cửa sổ/engine/Lab, scan phải được quét theo một bảng màu CÙNG
     * {@code (product_line, production_batch)} với bảng đang backfill ({@code scan.chart_id} là
     * bảng có hiệu lực lúc quét). Thiếu điều kiện này thì backfill bảng {@code MINI} ghi đè kết
     * quả của mọi scan {@code STANDARD}. SQL thuần đọc {@code color_chart} — cùng cách
     * {@link JdbcScanQueryRepository} đã JOIN bảng đó, không dùng type Java của colorchart.</p>
     */
    String BACKFILL_CANDIDATE_FILTER = """
             FROM scan_analysis a
             JOIN scan s ON s.id = a.scan_id
            WHERE a.is_current
              AND a.lab_l IS NOT NULL AND a.lab_a IS NOT NULL AND a.lab_b IS NOT NULL
              AND a.computed_at >= :computedFrom
              AND a.engine_version LIKE CONCAT(:engineVersionPrefix, '%')
              AND s.chart_id IN (SELECT c.id
                                   FROM color_chart c
                                   JOIN color_chart target ON target.id = :chartId
                                  WHERE c.product_line IS NOT DISTINCT FROM target.product_line
                                    AND c.production_batch IS NOT DISTINCT FROM target.production_batch)
            """;

    Optional<ScanAnalysis> findByScanIdAndCurrentTrue(UUID scanId);

    List<ScanAnalysis> findByScanIdOrderByComputedAtDesc(UUID scanId);

    /**
     * Ứng viên backfill (p6 §6.5.4). Sắp theo {@code computed_at, id} — khoá sắp xếp phải DUY
     * NHẤT, nếu không phân trang bằng offset có thể bỏ sót hoặc trả trùng dòng khi nhiều scan
     * có cùng {@code computed_at} (thật: hai scan trong cùng millisecond lúc tải cao).
     */
    @Query(value = "SELECT a.*" + BACKFILL_CANDIDATE_FILTER + " ORDER BY a.computed_at ASC, a.id ASC",
            nativeQuery = true)
    List<ScanAnalysis> findBackfillCandidates(@Param("chartId") UUID chartId,
                                              @Param("computedFrom") Instant computedFrom,
                                              @Param("engineVersionPrefix") String engineVersionPrefix,
                                              Pageable pageable);

    @Query(value = "SELECT count(*)" + BACKFILL_CANDIDATE_FILTER, nativeQuery = true)
    long countBackfillCandidates(@Param("chartId") UUID chartId,
                                 @Param("computedFrom") Instant computedFrom,
                                 @Param("engineVersionPrefix") String engineVersionPrefix);
}
