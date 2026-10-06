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

    Optional<ScanAnalysis> findByScanIdAndCurrentTrue(UUID scanId);

    List<ScanAnalysis> findByScanIdOrderByComputedAtDesc(UUID scanId);

    /**
     * Ứng viên backfill (p6 §6.5.4). Sắp theo {@code computedAt, id} — khoá sắp xếp phải DUY
     * NHẤT, nếu không phân trang bằng offset có thể bỏ sót hoặc trả trùng dòng khi nhiều scan
     * có cùng {@code computed_at} (thật: hai scan trong cùng millisecond lúc tải cao).
     */
    @Query("SELECT a FROM ScanAnalysis a"
            + " WHERE a.current = true"
            + "   AND a.labL IS NOT NULL AND a.labA IS NOT NULL AND a.labB IS NOT NULL"
            + "   AND a.computedAt >= :computedFrom"
            + "   AND a.engineVersion LIKE CONCAT(:engineVersionPrefix, '%')"
            + " ORDER BY a.computedAt ASC, a.id ASC")
    List<ScanAnalysis> findBackfillCandidates(@Param("computedFrom") Instant computedFrom,
                                              @Param("engineVersionPrefix") String engineVersionPrefix,
                                              Pageable pageable);

    @Query("SELECT count(a) FROM ScanAnalysis a"
            + " WHERE a.current = true"
            + "   AND a.labL IS NOT NULL AND a.labA IS NOT NULL AND a.labB IS NOT NULL"
            + "   AND a.computedAt >= :computedFrom"
            + "   AND a.engineVersion LIKE CONCAT(:engineVersionPrefix, '%')")
    long countBackfillCandidates(@Param("computedFrom") Instant computedFrom,
                                 @Param("engineVersionPrefix") String engineVersionPrefix);
}
